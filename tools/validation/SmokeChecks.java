package herzium.validation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.spongepowered.asm.mixin.MixinEnvironment;

/** Runs only in the separate validation mod, never in a release JAR. */
public final class SmokeChecks {
    private static int phase;
    private static long phaseStarted = System.nanoTime();
    private static Object title;
    private static Object settings;

    public static void tick(Object minecraft) {
        try {
            run(minecraft);
        } catch (Throwable failure) {
            System.err.println("[HERZIUM-SMOKE] FAIL");
            failure.printStackTrace();
            System.exit(2);
        }
    }

    private static void run(Object minecraft) throws Exception {
        Object gui = System.getProperty("herzium.smoke.guiField", "").isEmpty()
                ? minecraft : field(minecraft, System.getProperty("herzium.smoke.guiField"));
        Object screen = gui == minecraft
                ? field(minecraft, System.getProperty("herzium.smoke.screenField"))
                : call(gui, "screen");
        double seconds = (System.nanoTime() - phaseStarted) / 1_000_000_000.0;
        if (seconds > 90) throw new AssertionError("Phase timed out: " + phase + ", screen=" + screen);
        if (phase == 4 && Boolean.getBoolean("herzium.smoke.gameplay")) {
            boolean finished = (boolean) Class.forName("herzium.validation.Gameplay263")
                    .getMethod("tick", Object.class).invoke(null, minecraft);
            if (finished) {
                System.out.println("[HERZIUM-SMOKE] PASS");
                advance();
                call(minecraft, System.getProperty("herzium.smoke.stop"));
            }
            return;
        }
        if (screen == null) return;
        if (phase == 0 && screen.getClass().getName().endsWith("HerziumWarningScreen") && seconds > 3) {
            call(screen, "finish");
            System.out.println("[HERZIUM-SMOKE] Advisory accepted through its real continuation");
            advance();
        } else if (phase == 1 && screen.getClass().getName().equals(System.getProperty("herzium.smoke.titleClass"))) {
            title = screen;
            for (String target : System.getProperty("herzium.smoke.targets").split(",")) {
                Class<?> type = Class.forName(target, false, minecraft.getClass().getClassLoader());
                if (Arrays.stream(type.getDeclaredMethods()).noneMatch(m -> m.getName().contains("herzium$"))) {
                    throw new AssertionError("Missing transformed Herzium methods in " + target);
                }
            }
            MixinEnvironment.getCurrentEnvironment().audit();
            Object config = Class.forName("dev.zymekoh.herzium.config.HerziumConfig").getMethod("get").invoke(null);
            if (!call(config, "hotbarOrder").toString().equals("HERZIUM")) {
                throw new AssertionError("Fresh config lost HERZIUM default");
            }
            for (String expected : new String[]{"VANILLA_REVERSED", "VANILLA", "HERZIUM"}) {
                call(config, "cycleHotbarOrder");
                if (!call(config, "hotbarOrder").toString().equals(expected)) {
                    throw new AssertionError("Wrong selection-order cycle, expected " + expected);
                }
            }
            Class<?> screenType = Class.forName(System.getProperty("herzium.smoke.screenClass"));
            if (Boolean.getBoolean("herzium.smoke.modmenu")) {
                settings = Class.forName("com.terraformersmc.modmenu.ModMenu")
                        .getMethod("getConfigScreen", String.class, screenType).invoke(null, "herzium", title);
                if (settings == null) throw new AssertionError("Mod Menu did not register Herzium's screen factory");
                Object heading = call(settings, System.getProperty("herzium.smoke.getTitle"));
                String text = (String) call(heading, System.getProperty("herzium.smoke.getString"));
                if (text.startsWith("herzium.")) throw new AssertionError("Fabric API did not load translations: " + text);
                System.out.println("[HERZIUM-SMOKE] Mod Menu factory and localized settings title passed");
            } else {
                settings = Class.forName("dev.zymekoh.herzium.gui.HerziumConfigScreen")
                        .getConstructor(screenType).newInstance(title);
            }
            setScreen(gui, screenType, settings);
            System.out.println("[HERZIUM-SMOKE] All required targets transformed; title, defaults and order cycle passed");
            advance();
        } else if (phase == 2 && screen == settings && seconds > 2) {
            call(settings, System.getProperty("herzium.smoke.onClose"));
            System.out.println("[HERZIUM-SMOKE] Settings rendered and closed through its real handler");
            advance();
        } else if (phase == 3 && screen == title && seconds > 1) {
            if (Boolean.getBoolean("herzium.smoke.gameplay")) {
                advance();
                Class.forName("herzium.validation.Gameplay263").getMethod("start", Object.class).invoke(null, minecraft);
                return;
            }
            System.out.println("[HERZIUM-SMOKE] PASS");
            advance();
            call(minecraft, System.getProperty("herzium.smoke.stop"));
        }
    }

    private static void setScreen(Object owner, Class<?> screenType, Object screen) throws Exception {
        Method method = owner.getClass().getMethod(System.getProperty("herzium.smoke.setScreen"), screenType);
        method.invoke(owner, screen);
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Object call(Object owner, String name) throws Exception {
        try {
            return owner.getClass().getMethod(name).invoke(owner);
        } catch (NoSuchMethodException missingPublicMethod) {
            // Private advisory handlers are deliberately tested through reflection.
        }
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(owner);
            } catch (NoSuchMethodException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void advance() {
        phase++;
        phaseStarted = System.nanoTime();
    }
}
