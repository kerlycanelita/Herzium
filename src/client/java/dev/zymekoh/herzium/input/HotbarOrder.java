package dev.zymekoh.herzium.input;

/** A selection preference, not a change to input sampling or action speed. */
public enum HotbarOrder {
    VANILLA,
    HERZIUM,
    VANILLA_REVERSED;

    public HotbarOrder next() {
        return switch (this) {
            case VANILLA -> HERZIUM;
            case HERZIUM -> VANILLA_REVERSED;
            case VANILLA_REVERSED -> VANILLA;
        };
    }

    public String translationKey() {
        return "herzium.config.order." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public int preferred(int previous, int candidate, long[] pressOrder) {
        if (previous < 0) return candidate;
        return switch (this) {
            case VANILLA -> Math.max(previous, candidate);
            case VANILLA_REVERSED -> Math.min(previous, candidate);
            // A duplicate physical binding has one event serial for all its
            // slots. Equal/unknown serials use Vanilla's deterministic tie-break.
            case HERZIUM -> pressOrder[candidate] > pressOrder[previous] ? candidate
                    : pressOrder[candidate] < pressOrder[previous] ? previous
                    : Math.max(previous, candidate);
        };
    }
}
