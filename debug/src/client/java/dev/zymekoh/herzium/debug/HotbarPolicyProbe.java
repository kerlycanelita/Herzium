package dev.zymekoh.herzium.debug;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

/** Optional read-only probe; old Herzium builds still use the Vanilla oracle. */
final class HotbarPolicyProbe {
    private static Method resolver;
    private static Method name;
    private static boolean initialized;
    private static boolean warned;

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> type = Class.forName("dev.zymekoh.herzium.input.HotbarOrderController");
            resolver = type.getMethod("previewPendingSlot", Minecraft.class);
            name = type.getMethod("configuredOrderName");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Expected with Herzium versions before the optional order setting.
        }
    }

    static Integer resolve(Minecraft minecraft) {
        initialize();
        try {
            return resolver == null ? null : (Integer) resolver.invoke(null, minecraft);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportFailure(exception);
            return null;
        }
    }

    static String orderName() {
        initialize();
        try {
            return name == null ? "VANILLA" : (String) name.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reportFailure(exception);
            return "UNKNOWN";
        }
    }

    private static void reportFailure(Exception exception) {
        if (!warned) {
            warned = true;
            DebugCollector.warn("ORDER_PROBE", "Could not read the configured hotbar order: " + exception.getClass().getSimpleName());
        }
    }
}
