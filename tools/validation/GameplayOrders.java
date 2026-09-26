package herzium.validation;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.input.HotbarOrder;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import dev.zymekoh.herzium.mixin.KeyMappingAccessor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Drives the three selection orders through the real client input path and the
 * real client tick, then records what the HUD showed and what the server was
 * sent. Packaged only in the validation mod, never in a release JAR.
 *
 * <p>Keys enter through {@code KeyboardHandler.keyPress}, the method GLFW's key
 * callback calls, from the end of a frame: the same place real events arrive.
 * Nothing here calls {@code handleKeybinds}; Vanilla's own tick resolves every
 * press.</p>
 */
public final class GameplayOrders {
    private static final int RELEASE = 0;
    private static final int PRESS = 1;
    private static final int REPEAT = 2;

    private record Act(long atMs, int slot, int action) {
    }

    private record Scenario(String kind, String name, HotbarOrder order, int startSlot,
            boolean align, long startDelayMs, List<Act> acts) {
    }

    private enum Stage { PREPARE, WAIT, RUN, SETTLE }

    private static Method keyPress;
    private static Field suspendedField;
    private static boolean creating;
    private static int worldFrames;
    private static List<Scenario> plan;
    private static int index;
    private static Stage stage = Stage.PREPARE;
    private static int stageStartTick;
    private static long waitUntilNanos;
    private static long runStartNanos;
    private static int nextAct;
    private static final List<Map<String, Object>> results = new ArrayList<>();

    private static int tick;
    private static boolean inTick;
    private static boolean tickEndedSinceFrame;
    private static int lastSentSlot = -1;
    private static long frames;
    private static long firstFrameNanos;

    private static int lastPressSlot = -1;
    private static int target = -1;
    private static int claim = -1;
    private static int ghosts;
    private static int duplicates;
    private static int outOfTick;
    private static int framesSincePress;
    private static int framesToHud;
    private static int suspensions;
    private static long lastPressNanos;
    private static long hudNanos;
    private static long realNanos;
    private static long packetNanos;
    private static List<Integer> realAfterLastAct = new ArrayList<>();
    private static List<Integer> packetSlots = new ArrayList<>();

    private GameplayOrders() {
    }

    public static void start(Object client) {
        CreateWorldScreen.testWorld((Minecraft) client, () -> { });
    }

    /** Called once per frame from the end of {@code Minecraft.runTick}. */
    public static boolean tick(Object client) throws Exception {
        Minecraft minecraft = (Minecraft) client;
        if (!creating && minecraft.screen instanceof CreateWorldScreen screen) {
            screen.getUiState().setName("Herzium order validation");
            screen.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            screen.getUiState().setAllowCommands(true);
            creating = true;
            Method create = CreateWorldScreen.class.getDeclaredMethod(System.getProperty("herzium.orders.onCreate"));
            create.setAccessible(true);
            create.invoke(screen);
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return false;
        }
        if (++worldFrames < 120) {
            return false;
        }
        if (plan == null) {
            setup(minecraft);
        }

        long now = System.nanoTime();
        frames++;
        if (firstFrameNanos == 0L) {
            firstFrameNanos = now;
        }
        Inventory inventory = minecraft.player.getInventory();
        int real = inventory.getSelectedSlot();
        // What the HUD hook returns for this frame. The frame has already been
        // rendered and no event has arrived since, so this is what the player saw.
        int hud = ImmediateHotbarInput.visualSelectedSlot(inventory, real);
        boolean tickEnded = tickEndedSinceFrame;
        tickEndedSinceFrame = false;

        if (stage == Stage.RUN || stage == Stage.SETTLE) {
            if (lastPressNanos != 0L) {
                framesSincePress++;
                if (hud == target && hudNanos == 0L) {
                    hudNanos = now - lastPressNanos;
                    framesToHud = framesSincePress;
                }
            }
            // A HUD ahead of the real slot is a promise about the next tick.
            if (hud != real) {
                claim = hud;
            }
        }

        Scenario scenario = plan.get(index);
        switch (stage) {
            case PREPARE -> {
                HerziumConfig config = HerziumConfig.get();
                while (config.hotbarOrder() != scenario.order()) {
                    config.cycleHotbarOrder();
                }
                inventory.setSelectedSlot(scenario.startSlot());
                ImmediateHotbarInput.clearPreview();
                stage = Stage.WAIT;
                stageStartTick = tick;
                waitUntilNanos = 0L;
            }
            case WAIT -> {
                // Let the baseline slot reach the server first.
                if (tick - stageStartTick < 3 || pendingClicks(minecraft) > 0) {
                    break;
                }
                if (waitUntilNanos == 0L) {
                    waitUntilNanos = now + scenario.startDelayMs() * 1_000_000L;
                }
                if (scenario.align() ? !tickEnded : now < waitUntilNanos) {
                    break;
                }
                stage = Stage.RUN;
                runStartNanos = now;
                nextAct = 0;
                runDueActs(minecraft, scenario, now);
            }
            case RUN -> runDueActs(minecraft, scenario, now);
            case SETTLE -> {
                boolean quiet = pendingClicks(minecraft) == 0 && tick - stageStartTick >= 4 && hud == real;
                boolean timedOut = tick - stageStartTick > 80;
                if (quiet || timedOut) {
                    record(scenario, real, timedOut);
                    if (++index >= plan.size()) {
                        finish(minecraft);
                        return true;
                    }
                    stage = Stage.PREPARE;
                }
            }
        }
        return false;
    }

    public static void onTickHead() {
        tick++;
        inTick = true;
    }

    public static void onTickReturn() {
        inTick = false;
        tickEndedSinceFrame = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(stage == Stage.RUN || stage == Stage.SETTLE)) {
            return;
        }
        int real = minecraft.player.getInventory().getSelectedSlot();
        if (claim >= 0) {
            if (real != claim) {
                ghosts++;
            }
            claim = -1;
        }
        if (lastPressNanos != 0L && real == target && realNanos == 0L) {
            realNanos = System.nanoTime() - lastPressNanos;
        }
        if (stage == Stage.SETTLE) {
            realAfterLastAct.add(real);
        }
    }

    public static void onSend(Object packet) {
        if (!(packet instanceof ServerboundSetCarriedItemPacket carried)) {
            return;
        }
        int slot = carried.getSlot();
        boolean duplicate = slot == lastSentSlot;
        lastSentSlot = slot;
        if (stage == Stage.RUN || stage == Stage.SETTLE) {
            packetSlots.add(slot + 1);
            if (duplicate) {
                duplicates++;
            }
            if (!inTick) {
                outOfTick++;
            }
            if (lastPressNanos != 0L && slot == target && packetNanos == 0L) {
                packetNanos = System.nanoTime() - lastPressNanos;
            }
        }
    }

    private static void setup(Minecraft minecraft) throws Exception {
        keyPress = KeyboardHandler.class.getDeclaredMethod(
                System.getProperty("herzium.orders.keyPress"), long.class, int.class, KeyEvent.class);
        keyPress.setAccessible(true);
        suspendedField = ImmediateHotbarInput.class.getDeclaredField("suspended");
        suspendedField.setAccessible(true);
        Item[] ordinary = {Items.STONE, Items.DIRT, Items.OAK_PLANKS, Items.GLASS, Items.SAND,
                Items.GRAVEL, Items.COBBLESTONE, Items.BRICKS, Items.BOOKSHELF};
        for (int slot = 0; slot < 9; slot++) {
            minecraft.player.getInventory().setItem(slot, new ItemStack(ordinary[slot]));
        }
        plan = buildPlan();
    }

    private static List<Scenario> buildPlan() {
        List<Scenario> scenarios = new ArrayList<>();
        for (HotbarOrder order : HotbarOrder.values()) {
            scenarios.add(det(order, "9 then 1, same frame", taps(0, 8, 0, 0)));
            scenarios.add(det(order, "1 then 9, same frame", taps(0, 0, 0, 8)));
            scenarios.add(det(order, "9 then 1, 8 ms apart", taps(0, 8, 8, 0)));
            scenarios.add(det(order, "2, 7, 4 inside one tick", taps(0, 1, 10, 6, 20, 3)));
            scenarios.add(det(order, "1-9-1-9, 4 ms apart", taps(0, 0, 4, 8, 8, 0, 12, 8)));
            scenarios.add(det(order, "1 twice then 9, same frame", taps(0, 0, 0, 0, 0, 8)));
            scenarios.add(det(order, "9 then 1, next tick", taps(0, 8, 75, 0)));
            List<Act> hold = new ArrayList<>();
            hold.add(new Act(0, 0, PRESS));
            for (long at = 500; at < 1480; at += 33) {
                hold.add(new Act(at, 0, REPEAT));
            }
            hold.add(new Act(1480, 0, RELEASE));
            hold.addAll(taps(1500, 8));
            scenarios.add(det(order, "hold 1 (key repeat), then 9", hold));
        }

        Random random = new Random(20260925L);
        for (HotbarOrder order : new HotbarOrder[] {HotbarOrder.HERZIUM, HotbarOrder.VANILLA}) {
            for (int i = 0; i < 25; i++) {
                int start = random.nextInt(9);
                int pressed = (start + 1 + random.nextInt(8)) % 9;
                scenarios.add(new Scenario("latency", "single tap", order, start, false,
                        random.nextInt(50), taps(0, pressed)));
            }
        }

        long[] gaps = {0, 0, 4, 8, 16, 30, 45};
        List<Scenario> bursts = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            int presses = 2 + random.nextInt(3);
            List<Act> acts = new ArrayList<>();
            long at = 0;
            for (int press = 0; press < presses; press++) {
                at += press == 0 ? 0 : gaps[random.nextInt(gaps.length)];
                acts.addAll(taps(at, random.nextInt(9)));
            }
            bursts.add(new Scenario("stress", "burst " + i, HotbarOrder.VANILLA, random.nextInt(9), false,
                    random.nextInt(50), acts));
        }
        for (HotbarOrder order : HotbarOrder.values()) {
            for (Scenario burst : bursts) {
                scenarios.add(new Scenario(burst.kind(), burst.name(), order, burst.startSlot(), burst.align(),
                        burst.startDelayMs(), burst.acts()));
            }
        }
        return scenarios;
    }

    private static Scenario det(HotbarOrder order, String name, List<Act> acts) {
        return new Scenario("scenario", name, order, 4, true, 0, acts);
    }

    /** Pairs of (time in ms, slot): each becomes a press and release in the same frame. */
    private static List<Act> taps(long... timeSlotPairs) {
        List<Act> acts = new ArrayList<>();
        for (int i = 0; i < timeSlotPairs.length; i += 2) {
            acts.add(new Act(timeSlotPairs[i], (int) timeSlotPairs[i + 1], PRESS));
            acts.add(new Act(timeSlotPairs[i], (int) timeSlotPairs[i + 1], RELEASE));
        }
        return acts;
    }

    private static void runDueActs(Minecraft minecraft, Scenario scenario, long now) throws Exception {
        long elapsedMs = (now - runStartNanos) / 1_000_000L;
        while (nextAct < scenario.acts().size() && scenario.acts().get(nextAct).atMs() <= elapsedMs) {
            Act act = scenario.acts().get(nextAct++);
            InputConstants.Key bound =
                    ((KeyMappingAccessor) minecraft.options.keyHotbarSlots[act.slot()]).herzium$getBoundKey();
            keyPress.invoke(minecraft.keyboardHandler, minecraft.getWindow().handle(), act.action(),
                    new KeyEvent(bound.getValue(), 0, 0));
            if (act.action() != RELEASE) {
                lastPressSlot = act.slot();
                target = act.slot();
                lastPressNanos = System.nanoTime();
                framesSincePress = 0;
                hudNanos = 0L;
                realNanos = 0L;
                packetNanos = 0L;
                // A newer input supersedes whatever the HUD showed before it.
                claim = -1;
            }
        }
        if (nextAct >= scenario.acts().size()) {
            stage = Stage.SETTLE;
            stageStartTick = tick;
            realAfterLastAct = new ArrayList<>();
        }
    }

    private static void record(Scenario scenario, int finalSlot, boolean timedOut) throws Exception {
        boolean suspended = suspendedField.getBoolean(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", scenario.kind());
        result.put("name", scenario.name());
        result.put("order", scenario.order().name());
        result.put("start", scenario.startSlot() + 1);
        result.put("lastPressed", lastPressSlot + 1);
        result.put("final", finalSlot + 1);
        result.put("ticksAfterLastInput", toOneBased(realAfterLastAct));
        result.put("changesAfterFirstCommit", changesAfterFirstCommit(realAfterLastAct));
        result.put("ghostFrames", ghosts);
        result.put("carriedPackets", new ArrayList<>(packetSlots));
        result.put("duplicatePackets", duplicates);
        result.put("packetsOutsideTick", outOfTick);
        result.put("hudMs", hudNanos == 0L ? null : hudNanos / 1_000_000.0);
        result.put("framesToHud", hudNanos == 0L ? null : framesToHud);
        result.put("realMs", realNanos == 0L ? null : realNanos / 1_000_000.0);
        result.put("packetMs", packetNanos == 0L ? null : packetNanos / 1_000_000.0);
        result.put("suspended", suspended);
        result.put("timedOut", timedOut);
        results.add(result);
        if (suspended) {
            suspensions++;
            // Re-arm the preview so the remaining scenarios still measure it.
            ImmediateHotbarInput.resetSession();
        }
        lastPressSlot = -1;
        target = -1;
        claim = -1;
        ghosts = 0;
        duplicates = 0;
        outOfTick = 0;
        lastPressNanos = 0L;
        hudNanos = 0L;
        realNanos = 0L;
        packetNanos = 0L;
        packetSlots = new ArrayList<>();
        realAfterLastAct = new ArrayList<>();
    }

    private static void finish(Minecraft minecraft) throws Exception {
        HerziumConfig config = HerziumConfig.get();
        while (config.hotbarOrder() != HotbarOrder.HERZIUM) {
            config.cycleHotbarOrder();
        }
        double seconds = (System.nanoTime() - firstFrameNanos) / 1_000_000_000.0;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("fps", Math.round(frames / seconds));
        report.put("seconds", Math.round(seconds));
        report.put("previewSuspensions", suspensions);
        report.put("results", results);
        Files.writeString(Path.of(System.getProperty("herzium.orders.report")), json(report), StandardCharsets.UTF_8);
        System.out.println("[HERZIUM-SMOKE] Order validation finished: " + results.size() + " scenarios at "
                + report.get("fps") + " fps, " + suspensions + " preview suspensions");
    }

    private static int pendingClicks(Minecraft minecraft) {
        int pending = 0;
        for (var mapping : minecraft.options.keyHotbarSlots) {
            pending += ((KeyMappingAccessor) mapping).herzium$getPendingClickCount();
        }
        return pending;
    }

    private static List<Integer> toOneBased(List<Integer> slots) {
        List<Integer> oneBased = new ArrayList<>();
        for (int slot : slots) {
            oneBased.add(slot + 1);
        }
        return oneBased;
    }

    /** Selection changes after the first tick that followed the last input. */
    private static int changesAfterFirstCommit(List<Integer> slots) {
        int changes = 0;
        for (int i = 1; i < slots.size(); i++) {
            if (!slots.get(i).equals(slots.get(i - 1))) {
                changes++;
            }
        }
        return changes;
    }

    private static String json(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, entry) -> entries.add(json(String.valueOf(key)) + ":" + json(entry)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof List<?> list) {
            List<String> entries = new ArrayList<>();
            list.forEach(entry -> entries.add(json(entry)));
            return "[" + String.join(",", entries) + "]";
        }
        return json(value.toString());
    }
}
