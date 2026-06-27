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
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillModifierApplier;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /chronicles …} op-only admin command for inspecting and mutating player leveling state.
 *
 * <p>{@code level} and {@code points} are deliberately separate axes: {@code level add} does NOT
 * implicitly grant unspent points. The natural level-up flow that does grant points lives in
 * {@link dev.muon.chronicles_leveling.level.PlayerLevelManager#tryLevelUp}; admin commands set state
 * directly so they stay idempotent and side-effect-free. To simulate N natural level-ups, chain
 * {@code level add N; points add N*pointsPerLevel}.
 *
 * <p>Validation is minimal by design. Level clamps to {@code >= 1} (else the screen renders
 * {@code Lv. 0}); allocations clamp to {@code >= 0}. Level, allocations, and unspent points are
 * independent in the data model and not cross-validated, matching the convention that admin commands
 * bypass game-rule limits. {@code maxLevel} / {@code maxStatLevel} still gate the {@code +} buttons in
 * the screen, so over-allocation via command can't propagate to a player exploit.
 *
 * <p>Reset semantics: {@code reset <targets>} wipes to {@link PlayerLevelData#DEFAULT} and grants
 * {@link ConfigStats#startingPoints}, mirroring first-time login; {@code reset <targets> <stat>}
 * refunds that stat's allocation into {@code unspentPoints} and clears it (the respec primitive).
 */
public final class ChroniclesCommands {

    private static final SimpleCommandExceptionType UNKNOWN_STAT = new SimpleCommandExceptionType(
            Component.translatable("chronicles_leveling.command.error.unknown_stat"));
    private static final SimpleCommandExceptionType UNKNOWN_SKILL = new SimpleCommandExceptionType(
            Component.translatable("chronicles_leveling.command.error.unknown_skill"));

    private static final SuggestionProvider<CommandSourceStack> STAT_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ModStats.ALL.stream().map(ModStats.Entry::id), builder);

    private static final SuggestionProvider<CommandSourceStack> SKILL_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    SkillRegistry.all().stream().map(SkillDefinition::id), builder);

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
                                                        .executes(ChroniclesCommands::setLevel)
                                                        .then(Commands.argument("stat", StringArgumentType.word())
                                                                .suggests(STAT_SUGGESTIONS)
                                                                .executes(ChroniclesCommands::setStat)))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                        .executes(ChroniclesCommands::addLevel)
                                                        .then(Commands.argument("stat", StringArgumentType.word())
                                                                .suggests(STAT_SUGGESTIONS)
                                                                .executes(ChroniclesCommands::addStat)))))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ChroniclesCommands::resetAll)
                                                .then(Commands.argument("stat", StringArgumentType.word())
                                                        .suggests(STAT_SUGGESTIONS)
                                                        .executes(ChroniclesCommands::resetStat)))))
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
                        .then(Commands.literal("skill")
                                .then(Commands.literal("get")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> getSkill(ctx, null))
                                                .then(Commands.argument("skill", StringArgumentType.word())
                                                        .suggests(SKILL_SUGGESTIONS)
                                                        .executes(ctx -> getSkill(ctx, skillArg(ctx))))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("skill", StringArgumentType.word())
                                                        .suggests(SKILL_SUGGESTIONS)
                                                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                                                .executes(ChroniclesCommands::setSkillLevel)))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("skill", StringArgumentType.word())
                                                        .suggests(SKILL_SUGGESTIONS)
                                                        .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                                .executes(ChroniclesCommands::addSkillLevel)))))
                                .then(Commands.literal("xp")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("skill", StringArgumentType.word())
                                                        .suggests(SKILL_SUGGESTIONS)
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                .executes(ChroniclesCommands::grantSkillXp)))))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ChroniclesCommands::resetAllSkills)
                                                .then(Commands.argument("skill", StringArgumentType.word())
                                                        .suggests(SKILL_SUGGESTIONS)
                                                        .executes(ChroniclesCommands::resetSkill)))))
                        .then(Commands.literal("cooldowns")
                                .then(Commands.literal("reset")
                                        .executes(ctx -> resetCooldowns(ctx,
                                                List.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> resetCooldowns(ctx,
                                                        EntityArgument.getPlayers(ctx, "targets"))))))
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

    // --- skill -------------------------------------------------------------
    // Symmetric with the level/points axes, for balance debugging. `set`/`add` move the skill LEVEL
    // (resetting XP into the level), `xp` banks raw XP through the real curve/level-up path, and `reset`
    // is the respec primitive (clear perks + refund, keep level/xp). Skill levels are clamped to
    // [1, maxSkillLevel]; reset recomputes modifiers and drops now-relocked ability bindings.

    private static String skillArg(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "skill");
    }

    private static String requireKnownSkill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String skillId = skillArg(ctx);
        if (SkillRegistry.get(skillId) == null) throw UNKNOWN_SKILL.create();
        return skillId;
    }

    private static int clampSkillLevel(int level) {
        int cap = Configs.SKILLS.maxSkillLevel.get();
        int clamped = Math.max(1, level);
        return cap > 0 ? Math.min(clamped, cap) : clamped;
    }

    private static int getSkill(CommandContext<CommandSourceStack> ctx, String skillId) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        if (skillId != null && SkillRegistry.get(skillId) == null) throw UNKNOWN_SKILL.create();

        for (ServerPlayer target : targets) {
            PlayerSkillData data = PlayerSkillManager.get(target);
            if (skillId == null) {
                for (SkillDefinition def : SkillRegistry.all()) {
                    printSkillLine(ctx, target, def, data.get(def.id()));
                }
            } else {
                printSkillLine(ctx, target, SkillRegistry.get(skillId), data.get(skillId));
            }
        }
        return targets.size();
    }

    private static void printSkillLine(CommandContext<CommandSourceStack> ctx, ServerPlayer target,
                                       SkillDefinition def, PlayerSkillData.SkillEntry entry) {
        ctx.getSource().sendSuccess(() -> Component.translatable("chronicles_leveling.command.skill.get",
                        target.getName(), def.display(), entry.level(), entry.xp(), entry.spentPoints())
                .copy().withStyle(ChatFormatting.GRAY), false);
    }

    private static int setSkillLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String skillId = requireKnownSkill(ctx);
        int level = clampSkillLevel(IntegerArgumentType.getInteger(ctx, "level"));
        for (ServerPlayer target : targets) {
            PlayerSkillData.SkillEntry entry = PlayerSkillManager.getSkill(target, skillId);
            PlayerSkillManager.setSkill(target, skillId, entry.withLevel(level).withXp(0));
            SkillModifierApplier.recompute(target);
        }
        feedback(ctx, "chronicles_leveling.command.skill.set", targets.size(), skillDisplay(skillId), level);
        return targets.size();
    }

    private static int addSkillLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String skillId = requireKnownSkill(ctx);
        int delta = IntegerArgumentType.getInteger(ctx, "delta");
        for (ServerPlayer target : targets) {
            PlayerSkillData.SkillEntry entry = PlayerSkillManager.getSkill(target, skillId);
            PlayerSkillManager.setSkill(target, skillId, entry.withLevel(clampSkillLevel(entry.level() + delta)).withXp(0));
            SkillModifierApplier.recompute(target);
        }
        feedback(ctx, "chronicles_leveling.command.skill.add", targets.size(), skillDisplay(skillId), delta);
        return targets.size();
    }

    private static int grantSkillXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String skillId = requireKnownSkill(ctx);
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        for (ServerPlayer target : targets) {
            PlayerSkillManager.grantXp(target, skillId, amount);   // banks XP + rolls level-ups + recomputes
        }
        feedback(ctx, "chronicles_leveling.command.skill.xp", targets.size(), skillDisplay(skillId), amount);
        return targets.size();
    }

    private static int resetSkill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String skillId = requireKnownSkill(ctx);
        for (ServerPlayer target : targets) {
            PlayerSkillData.SkillEntry entry = PlayerSkillManager.getSkill(target, skillId);
            PlayerSkillManager.setSkill(target, skillId,
                    new PlayerSkillData.SkillEntry(entry.level(), entry.xp(), 0, Map.of()));
            SkillModifierApplier.recompute(target);
            PlayerSkillManager.reconcileAbilityBindings(target);   // drop slots/cooldowns for now-relocked abilities
        }
        feedback(ctx, "chronicles_leveling.command.skill.reset", targets.size(), skillDisplay(skillId));
        return targets.size();
    }

    private static int resetAllSkills(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer target : targets) {
            PlayerSkillData data = PlayerSkillManager.get(target);
            Map<String, PlayerSkillData.SkillEntry> cleared = new HashMap<>();
            data.skills().forEach((id, entry) ->
                    cleared.put(id, new PlayerSkillData.SkillEntry(entry.level(), entry.xp(), 0, Map.of())));
            PlayerSkillManager.set(target, new PlayerSkillData(cleared, data.abilityCooldownEnds(), data.abilitySlots()));
            SkillModifierApplier.recompute(target);
            PlayerSkillManager.reconcileAbilityBindings(target);
        }
        feedback(ctx, "chronicles_leveling.command.skill.reset_all", targets.size());
        return targets.size();
    }

    private static Component skillDisplay(String skillId) {
        SkillDefinition def = SkillRegistry.get(skillId);
        return def == null ? Component.literal(skillId) : def.display();
    }

    // --- cooldowns ---------------------------------------------------------

    private static int resetCooldowns(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer target : targets) {
            PlayerSkillManager.clearAbilityCooldowns(target);
        }
        feedback(ctx, "chronicles_leveling.command.cooldowns.reset", targets.size());
        return targets.size();
    }

    // --- helpers -----------------------------------------------------------

    private static void feedback(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        ctx.getSource().sendSuccess(() -> Component.translatable(key, args), true);
    }
}
