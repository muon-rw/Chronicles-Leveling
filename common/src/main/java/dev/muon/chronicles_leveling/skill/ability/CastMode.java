package dev.muon.chronicles_leveling.skill.ability;

/**
 * How an ability is cast. {@link #INSTANT} fires once on key press (the default). {@link #CHARGE} and
 * {@link #CHANNEL} are held casts: the client sends a start on press and a release on key-up, and the server
 * {@code HeldCastDriver} drives them. A charge builds up while held and fires once on release (scaled by hold
 * time); a channel runs a per-tick effect while held and ends on release, max duration, or a failed re-gate.
 */
public enum CastMode {
    INSTANT,
    CHARGE,
    CHANNEL
}
