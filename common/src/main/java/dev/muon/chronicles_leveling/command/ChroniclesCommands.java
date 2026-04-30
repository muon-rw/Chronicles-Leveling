package dev.muon.chronicles_leveling.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.muon.chronicles_leveling.config.ConfigStats;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.stat.ModStats;
import dev.muon.chronicles_leveling.stat.StatModifierApplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Collection;

/**
 * {@code /chronicles level …} — admin command for inspecting and mutating
 * player leveling state. Permission level 2 (op-only).
 *
 * <p>Subcommand tree:
 * <pre>
 *   /chronicles level  get   &lt;targets&gt; [&lt;stat&gt;]
 *   /chronicles level  set   &lt;targets&gt; &lt;amount&gt; [&lt;stat&gt;]
 *   /chronicles level  add   &lt;targets&gt; &lt;delta&gt;  [&lt;stat&gt;]
 *   /chronicles level  reset &lt;targets&gt; [&lt;stat&gt;]
 *   /chronicles points get   &lt;targets&gt;
 *   /chronicles points set   &lt;targets&gt; &lt;amount&gt;
 *   /chronicles points add   &lt;targets&gt; &lt;delta&gt;
 * </pre>
 *
 * <p>{@code level} and {@code points} are deliberately separate axes — {@code level add}
 * does <i>not</i> implicitly grant unspent points. The natural level-up flow that
 * does grant points lives in {@link dev.muon.chronicles_leveling.level.PlayerLevelManager#tryLevelUp};
 * admin commands set state directly so they're idempotent and side-effect-free.
 * For "simulate N natural level-ups", chain {@code level add N; points add N*pointsPerLevel}.
 *
 * <p>With {@code &lt;stat&gt;} omitted on the {@code level} verbs the operation
 * targets the player level itself; with {@code &lt;stat&gt;} it targets that
 * stat's allocation.
 *
 * <p><b>Validation policy:</b> minimal. We clamp level to {@code >= 1}
 * (otherwise the screen renders {@code Lv. 0}); allocations clamp to
 * {@code >= 0} via {@link PlayerLevelData#withAllocation}. We deliberately do
 * <i>not</i> cross-validate level vs. allocations vs. unspent points — the
 * three fields are independent in the data model, and admin commands by
 * convention bypass game-rule limits. {@code maxLevel} / {@code maxStatLevel}
 * still gate the {@code +} buttons in the screen, so over-allocation via
 * command can't propagate to a player exploit.
 *
 * <p><b>Reset semantics:</b>
 * <ul>
 *   <li>{@code reset &lt;targets&gt;} — wipe to {@link PlayerLevelData#DEFAULT}
 *       and grant {@link ConfigStats#startingPoints}.
 *       Mirrors first-time login.</li>
 *   <li>{@code reset &lt;targets&gt; &lt;stat&gt;} — refund the stat's current
 *       allocation back into {@code unspentPoints} and clear that allocation.
 *       This is the respec primitive.</li>
 * </ul>
 */
public final class ChroniclesCommands {

    private static final SimpleCommandExceptionType UNKNOWN_STAT = new SimpleCommandExceptionType(
            Component.translatable("chronicles_leveling.command.error.unknown_stat"));

    private static final SuggestionProvider<CommandSourceStack> STAT_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ModStats.ALL.stream().map(ModStats.Entry::id), builder);

    private ChroniclesCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("chronicles")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("level")
                                .then(Commands.literal("get")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> getInfo(ctx, null))
                                                .then(Commands.argument("stat", StringArgumentType.word())
                                                        .suggests(STAT_SUGGESTIONS)
                                                        .executes(ctx -> getInfo(ctx, statArg(ctx))))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> setLevel(ctx))
                                                        .then(Commands.argument("stat", StringArgumentType.word())
                                                                .suggests(STAT_SUGGESTIONS)
                                                                .executes(ctx -> setStat(ctx))))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                        .executes(ctx -> addLevel(ctx))
                                                        .then(Commands.argument("stat", StringArgumentType.word())
                                                                .suggests(STAT_SUGGESTIONS)
                                                                .executes(ctx -> addStat(ctx))))))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> resetAll(ctx))
                                                .then(Commands.argument("stat", StringArgumentType.word())
                                                        .suggests(STAT_SUGGESTIONS)
                                                        .executes(ctx -> resetStat(ctx))))))
                        .then(Commands.literal("points")
                                .then(Commands.literal("get")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ChroniclesCommands::getPoints)))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(ChroniclesCommands::setPoints))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                        .executes(ChroniclesCommands::addPoints)))))
        );
    }

    private static String statArg(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "stat");
    }

    private static String requireKnownStat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String statId = statArg(ctx);
        if (!ModStats.isRegistered(statId)) throw UNKNOWN_STAT.create();
        return statId;
    }

    // --- get ---------------------------------------------------------------

    private static int getInfo(CommandContext<CommandSourceStack> ctx, String statId) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        if (statId != null && !ModStats.isRegistered(statId)) throw UNKNOWN_STAT.create();

        for (ServerPlayer target : targets) {
            PlayerLevelData data = PlayerLevelManager.get(target);
            Component msg;
            if (statId == null) {
                msg = Component.translatable("chronicles_leveling.command.level.get",
                        target.getName(), data.level(), data.unspentPoints());
            } else {
                msg = Component.translatable("chronicles_leveling.command.level.get_stat",
                        target.getName(),
                        Component.translatable("chronicles_leveling.stat." + statId),
                        data.allocation(statId));
            }
            ctx.getSource().sendSuccess(() -> msg.copy().withStyle(ChatFormatting.GRAY), false);
        }
        return targets.size();
    }

    // --- set ---------------------------------------------------------------

    private static int setLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int amount = Math.max(1, IntegerArgumentType.getInteger(ctx, "amount"));
        for (ServerPlayer target : targets) {
            PlayerLevelData data = PlayerLevelManager.get(target);
            PlayerLevelManager.set(target, data.withLevel(amount));
        }
        feedback(ctx, "chronicles_leveling.command.level.set", targets.size(), amount);
        return targets.size();
    }

    private static int setStat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        String statId = requireKnownStat(ctx);
        Component statName = Component.translatable("chronicles_leveling.stat." + statId);

        for (ServerPlayer target : targets) {
            PlayerLevelData data = PlayerLevelManager.get(target);
            PlayerLevelManager.set(target, data.withAllocation(statId, amount));
            StatModifierApplier.recompute(target);
        }
        feedback(ctx, "chronicles_leveling.command.level.set_stat", targets.size(), statName, amount);
        return targets.size();
    }

    // --- add ---------------------------------------------------------------

    private static int addLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int delta = IntegerArgumentType.getInteger(ctx, "delta");
        for (ServerPlayer target : targets) {
            PlayerLevelData data = PlayerLevelManager.get(target);
            PlayerLevelManager.set(target, data.withLevel(Math.max(1, data.level() + delta)));
        }
        feedback(ctx, "chronicles_leveling.command.level.add", targets.size(), delta);
        return targets.size();
    }

    private static int addStat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int delta = IntegerArgumentType.getInteger(ctx, "delta");
        String statId = requireKnownStat(ctx);
        Component statName = Component.translatable("chronicles_leveling.stat." + statId);

        for (ServerPlayer target : targets) {
            PlayerLevelData data = PlayerLevelManager.get(target);
            int next = Math.max(0, data.allocation(statId) + delta);
            PlayerLevelManager.set(target, data.withAllocation(statId, next));
            StatModifierApplier.recompute(target);
        }
        feedback(ctx, "chronicles_leveling.command.level.add_stat", targets.size(), statName, delta);
        return targets.size();
    }

    // --- reset -------------------------------------------------------------

    private static int resetAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int starting = Configs.STATS.startingPoints.get();

        for (ServerPlayer target : targets) {
            PlayerLevelManager.set(target, PlayerLevelData.DEFAULT.withUnspentPoints(starting));
            StatModifierApplier.recompute(target);
        }
        feedback(ctx, "chronicles_leveling.command.level.reset", targets.size());
        return targets.size();
    }

    private static int resetStat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String statId = requireKnownStat(ctx);
        Component statName = Component.translatable("chronicles_leveling.stat." + statId);

        int totalRefunded = 0;
        for (ServerPlayer target : targets) {
            PlayerLevelData data = PlayerLevelManager.get(target);
            int refund = data.allocation(statId);
            totalRefunded += refund;
            PlayerLevelManager.set(target, data
                    .withAllocation(statId, 0)
                    .withUnspentPoints(data.unspentPoints() + refund));
            StatModifierApplier.recompute(target);
        }
        feedback(ctx, "chronicles_leveling.command.level.reset_stat", targets.size(), statName, totalRefunded);
        return targets.size();
    }

    // --- points ------------------------------------------------------------

    private static int getPoints(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer target : targets) {
            int points = PlayerLevelManager.get(target).unspentPoints();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "chronicles_leveling.command.points.get", target.getName(), points)
                    .copy().withStyle(ChatFormatting.GRAY), false);
        }
        return targets.size();
    }

    private static int setPoints(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        for (ServerPlayer target : targets) {
            PlayerLevelManager.set(target, PlayerLevelManager.get(target).withUnspentPoints(amount));
        }
        feedback(ctx, "chronicles_leveling.command.points.set", targets.size(), amount);
        return targets.size();
    }

    private static int addPoints(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int delta = IntegerArgumentType.getInteger(ctx, "delta");
        for (ServerPlayer target : targets) {
            PlayerLevelData data = PlayerLevelManager.get(target);
            PlayerLevelManager.set(target, data.withUnspentPoints(Math.max(0, data.unspentPoints() + delta)));
        }
        feedback(ctx, "chronicles_leveling.command.points.add", targets.size(), delta);
        return targets.size();
    }

    // --- helpers -----------------------------------------------------------

    private static void feedback(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        ctx.getSource().sendSuccess(() -> Component.translatable(key, args), true);
    }
}
