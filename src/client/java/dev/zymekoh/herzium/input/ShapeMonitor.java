package dev.zymekoh.herzium.input;

import dev.zymekoh.herzium.Herzium;
import dev.zymekoh.herzium.config.HerziumConfig;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

/**
 * Watches the shape of every tick this client sends, whoever caused it.
 *
 * <p>A server only sees packets, cut into ticks. Vanilla never sends a hotbar
 * change after a click of the same tick, and never the same hotbar slot twice
 * in a row; two placements in one tick are possible in Vanilla (two clicks
 * inside 50 ms) but uncommon enough that strict anticheats look at them. This
 * counts those three things in the client's own outgoing packets. It only
 * observes: nothing is delayed, dropped or changed, and the counts say when a
 * mod in this instance (Herzium or any other) is producing a tick a server
 * could tell from Vanilla's.</p>
 */
public final class ShapeMonitor {
    private static boolean acted;
    private static int placementsThisTick;
    private static int lastSlot = -1;
    private static volatile int ticks;
    private static volatile int slotAfterClick;
    private static volatile int repeatedSlot;
    private static volatile int doublePlacement;
    private static boolean warned;

    private ShapeMonitor() {
    }

    /** Called for every packet the client sends while playing. */
    public static void sent(Packet<?> packet) {
        if (packet instanceof ServerboundSetCarriedItemPacket carried) {
            if (!HerziumConfig.get().on(HerziumConfig.Feature.SHAPE_MONITOR)) {
                return;
            }
            if (acted) {
                slotAfterClick++;
                warn("a hotbar change after a click in the same tick");
            }
            if (carried.getSlot() == lastSlot) {
                repeatedSlot++;
                warn("the same hotbar slot twice in a row");
            }
            lastSlot = carried.getSlot();
        } else if (packet instanceof ServerboundUseItemOnPacket) {
            acted = true;
            if (++placementsThisTick == 2) {
                doublePlacement++;
            }
        } else if (packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundPlayerActionPacket action && digs(action)) {
            acted = true;
        }
    }

    /** Vanilla itself sends an offhand swap or a drop before the slot of the same tick: only digging counts. */
    private static boolean digs(ServerboundPlayerActionPacket packet) {
        return switch (packet.getAction()) {
            case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> true;
            default -> false;
        };
    }

    /** Called when the client tick ends; that is where the server cuts ticks too. */
    public static void tickEnded() {
        acted = false;
        placementsThisTick = 0;
        ticks++;
    }

    public static void reset() {
        acted = false;
        placementsThisTick = 0;
        lastSlot = -1;
        ticks = slotAfterClick = repeatedSlot = doublePlacement = 0;
        warned = false;
    }

    private static void warn(String what) {
        if (!warned) {
            warned = true;
            Herzium.LOGGER.warn("Tick shape monitor: this client sent {}. Vanilla never does; a mod in this "
                    + "instance is changing the order of your packets. The counters are in Herzium's settings.", what);
        }
    }

    public static int ticks() { return ticks; }

    public static int slotAfterClick() { return slotAfterClick; }

    public static int repeatedSlot() { return repeatedSlot; }

    public static int doublePlacement() { return doublePlacement; }
}
