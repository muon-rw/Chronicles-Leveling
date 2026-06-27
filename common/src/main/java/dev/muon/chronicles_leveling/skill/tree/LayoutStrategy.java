package dev.muon.chronicles_leveling.skill.tree;

/**
 * Turns a pure {@link SkillTopology} into pixel {@link TreeGeometry}. Implementations are
 * interchangeable per skill ({@link LayeredLayout} is the default branching layout,
 * {@link GridLayout} the paginated fallback) and the screen, canvas, and hit-test never branch on
 * which one ran; they consume the geometry uniformly.
 */
public interface LayoutStrategy {
    TreeGeometry place(SkillTopology topology);
}
