package dev.zymekoh.herzium.input;

/** A selection preference, not a change to input sampling or action speed. */
public enum HotbarOrder {
    VANILLA,
    HERZIUM,
    VANILLA_REVERSED,
    /**
     * Laboratory order: a burst that mixes hotbar keys and clicks inside one
     * tick is applied in the order it was pressed, within that tick, so one
     * tick can change the slot several times. Kept to measure it against the
     * Herzium order, which moves the later part of such a burst to the next
     * tick instead.
     */
    SAME_TICK;

    public HotbarOrder next() {
        return switch (this) {
            case VANILLA -> HERZIUM;
            case HERZIUM -> VANILLA_REVERSED;
            case VANILLA_REVERSED -> SAME_TICK;
            case SAME_TICK -> VANILLA;
        };
    }

    public String translationKey() {
        return "herzium.config.order." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Whether the last key pressed wins among the keys of one pass. */
    public boolean lastPressWins() {
        return this == HERZIUM || this == SAME_TICK;
    }

    public int preferred(int previous, int candidate, long[] pressOrder) {
        if (previous < 0) return candidate;
        return switch (this) {
            case VANILLA -> Math.max(previous, candidate);
            case VANILLA_REVERSED -> Math.min(previous, candidate);
            // A duplicate physical binding has one event serial for all its
            // slots. Equal/unknown serials use Vanilla's deterministic tie-break.
            case HERZIUM, SAME_TICK -> pressOrder[candidate] > pressOrder[previous] ? candidate
                    : pressOrder[candidate] < pressOrder[previous] ? previous
                    : Math.max(previous, candidate);
        };
    }
}
