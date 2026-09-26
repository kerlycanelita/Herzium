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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The crystal-PvP cycle on 26.2+: key 2 (obsidian), use, key 3 (end crystal),
 * use. Measures when the obsidian and the crystal really appear, under the
 * Vanilla and the Herzium order, at three input speeds. Validation only.
 */
public final class GameplayCrystal {
    private static final int RELEASE = 0;
    private static final int PRESS = 1;
    private static final int START_SLOT = 0;
    private static final int OBSIDIAN_SLOT = 1;
    private static final int CRYSTAL_SLOT = 2;
    private static final BlockPos TARGET = new BlockPos(4, 201, 2);
    private static final AABB CRYSTAL_BOX = new AABB(3, 201, 1, 6, 204, 4);
    private static final String[] SETUP = {
            "time set day", "fill 0 200 0 8 200 8 stone", "fill 0 201 0 8 205 8 air",
            "tp @s 4.5 201 1.5 0 55", "item replace entity @s hotbar.0 with stone",
            "item replace entity @s hotbar.1 with obsidian 64", "item replace entity @s hotbar.2 with end_crystal 64"};

    private record Speed(String name, long[] times) {
    }

    private record Cycle(HotbarOrder order, Speed speed, long delayMs) {
    }

    private enum Stage { SETUP, PREPARE, WAIT, RUN, SETTLE }

    private static boolean creating;
    private static int worldFrames;
    private static Method keyPress;
    private static Field handHeight;
    private static List<Cycle> plan;
    private static int index;
    private static Stage stage = Stage.SETUP;
    private static int stageTick;
    private static int tick;
    private static long waitUntil;
    private static long runStart;
    private static int nextAct;
    private static final long[] actNanos = new long[4];
    private static long obsidianNanos;
    private static long crystalNanos;
    private static long hudNanos;
    private static long commitNanos;
    private static float tickHeightAfterPlace = -1F;
    private static float renderHeightAfterPlace = -1F;
    private static final List<Map<String, Object>> results = new ArrayList<>();

    private GameplayCrystal() {
    }

    public static void start(Object client) {
        CreateWorldScreen.testWorld((Minecraft) client, () -> { });
    }

    public static boolean tick(Object client) throws Exception {
        Minecraft minecraft = (Minecraft) client;
        if (!creating && minecraft.gui.screen() instanceof CreateWorldScreen screen) {
            screen.getUiState().setName("Herzium crystal validation");
            screen.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            screen.getUiState().setAllowCommands(true);
            creating = true;
            Method create = CreateWorldScreen.class.getDeclaredMethod(System.getProperty("herzium.orders.onCreate"));
            create.setAccessible(true);
            create.invoke(screen);
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null) {
            return false;
        }
        if (++worldFrames < 120) {
            return false;
        }
        long now = System.nanoTime();
        Inventory inventory = minecraft.player.getInventory();
        int real = inventory.getSelectedSlot();
        int hud = ImmediateHotbarInput.visualSelectedSlot(inventory, real);

        if (stage == Stage.RUN || stage == Stage.SETTLE) {
            if (hudNanos == 0L && actNanos[0] != 0L && hud == OBSIDIAN_SLOT) {
                hudNanos = now;
            }
            if (obsidianNanos != 0L && renderHeightAfterPlace < 0F) {
                // Read after this frame rendered the hand: what the player saw.
                renderHeightAfterPlace = handHeight.getFloat(minecraft.gameRenderer.itemInHandRenderer);
            }
        }

        switch (stage) {
            case SETUP -> {
                keyPress = KeyboardHandler.class.getDeclaredMethod(
                        System.getProperty("herzium.orders.keyPress"), long.class, int.class, KeyEvent.class);
                keyPress.setAccessible(true);
                handHeight = ItemInHandRenderer.class.getDeclaredField("mainHandHeight");
                handHeight.setAccessible(true);
                for (String command : SETUP) {
                    minecraft.player.connection.sendCommand(command);
                }
                plan = buildPlan();
                stage = Stage.PREPARE;
                stageTick = tick + 40;
            }
            case PREPARE -> {
                if (tick < stageTick) {
                    break;
                }
                if (index == 0 && !(inventory.getItem(OBSIDIAN_SLOT).is(Items.OBSIDIAN)
                        && inventory.getItem(CRYSTAL_SLOT).is(Items.END_CRYSTAL))) {
                    throw new AssertionError("Server inventory was not set up");
                }
                HerziumConfig config = HerziumConfig.get();
                while (config.hotbarOrder() != plan.get(index).order()) {
                    config.cycleHotbarOrder();
                }
                // /tp cannot leave the world height, so the crystal is killed; the
                // platform is restored right after, in case that tore a hole
                // where the next cycle aims.
                minecraft.player.connection.sendCommand("kill @e[type=end_crystal]");
                minecraft.player.connection.sendCommand("fill 0 201 0 8 205 8 air");
                minecraft.player.connection.sendCommand("fill 0 200 0 8 200 8 stone");
                minecraft.player.connection.sendCommand("tp @s 4.5 201 1.5 0 55");
                inventory.setSelectedSlot(START_SLOT);
                ImmediateHotbarInput.clearPreview();
                stage = Stage.WAIT;
                stageTick = tick;
                waitUntil = 0L;
            }
            case WAIT -> {
                boolean clean = minecraft.level.getBlockState(TARGET).isAir()
                        && minecraft.level.getEntitiesOfClass(EndCrystal.class, CRYSTAL_BOX).isEmpty()
                        && minecraft.hitResult instanceof BlockHitResult hit
                        && hit.getBlockPos().equals(TARGET.below());
                if (tick - stageTick < 4 || !clean) {
                    if (tick - stageTick > 100) {
                        throw new AssertionError("Test spot never became clean: hit="
                                + (minecraft.hitResult instanceof BlockHitResult h ? h.getBlockPos() + " " + h.getDirection()
                                        : String.valueOf(minecraft.hitResult))
                                + " target=" + minecraft.level.getBlockState(TARGET)
                                + " crystals=" + minecraft.level.getEntitiesOfClass(EndCrystal.class, CRYSTAL_BOX).size()
                                + " player=" + minecraft.player.position() + " rot=" + minecraft.player.getYRot()
                                + "/" + minecraft.player.getXRot() + " cycle=" + index);
                    }
                    break;
                }
                if (waitUntil == 0L) {
                    waitUntil = now + plan.get(index).delayMs() * 1_000_000L;
                }
                if (now < waitUntil) {
                    break;
                }
                stage = Stage.RUN;
                runStart = now;
                nextAct = 0;
                java.util.Arrays.fill(actNanos, 0L);
                obsidianNanos = crystalNanos = hudNanos = commitNanos = 0L;
                tickHeightAfterPlace = renderHeightAfterPlace = -1F;
                runActs(minecraft, now);
            }
            case RUN -> runActs(minecraft, now);
            case SETTLE -> {
                if (tick - stageTick >= 24) {
                    record(minecraft);
                    if (++index >= plan.size()) {
                        finish();
                        return true;
                    }
                    stage = Stage.PREPARE;
                    stageTick = tick;
                }
            }
        }
        return false;
    }

    public static void onTickHead() {
        tick++;
    }

    public static void onTickReturn() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(stage == Stage.RUN || stage == Stage.SETTLE)) {
            return;
        }
        long now = System.nanoTime();
        if (commitNanos == 0L && actNanos[0] != 0L
                && minecraft.player.getInventory().getSelectedSlot() == OBSIDIAN_SLOT) {
            commitNanos = now;
        }
        if (obsidianNanos == 0L && minecraft.level.getBlockState(TARGET).is(Blocks.OBSIDIAN)) {
            obsidianNanos = now;
            try {
                // After this tick: what Vanilla would have drawn for the hand.
                tickHeightAfterPlace = handHeight.getFloat(minecraft.gameRenderer.itemInHandRenderer);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }
        if (crystalNanos == 0L && !minecraft.level.getEntitiesOfClass(EndCrystal.class, CRYSTAL_BOX).isEmpty()) {
            crystalNanos = now;
        }
    }

    public static void onSend(Object packet) {
    }

    private static List<Cycle> buildPlan() {
        Speed[] speeds = {
                new Speed("normal (60 ms)", new long[] {0, 60, 120, 180}),
                new Speed("rapido (25 ms)", new long[] {0, 25, 50, 75}),
                new Speed("rafaga (8 ms)", new long[] {0, 8, 16, 24})};
        Random random = new Random(2609L);
        List<Cycle> cycles = new ArrayList<>();
        for (HotbarOrder order : new HotbarOrder[] {HotbarOrder.VANILLA, HotbarOrder.HERZIUM}) {
            for (Speed speed : speeds) {
                for (int i = 0; i < 10; i++) {
                    cycles.add(new Cycle(order, speed, random.nextInt(50)));
                }
            }
        }
        return cycles;
    }

    private static void runActs(Minecraft minecraft, long now) throws Exception {
        long[] times = plan.get(index).speed().times();
        long elapsed = (now - runStart) / 1_000_000L;
        while (nextAct < 4 && times[nextAct] <= elapsed) {
            switch (nextAct) {
                case 0 -> tap(minecraft, OBSIDIAN_SLOT);
                case 1, 3 -> use(minecraft);
                case 2 -> tap(minecraft, CRYSTAL_SLOT);
                default -> throw new IllegalStateException();
            }
            actNanos[nextAct++] = System.nanoTime();
        }
        if (nextAct >= 4) {
            stage = Stage.SETTLE;
            stageTick = tick;
        }
    }

    private static void tap(Minecraft minecraft, int slot) throws Exception {
        InputConstants.Key key = ((KeyMappingAccessor) minecraft.options.keyHotbarSlots[slot]).herzium$getBoundKey();
        for (int action : new int[] {PRESS, RELEASE}) {
            keyPress.invoke(minecraft.keyboardHandler, minecraft.getWindow().handle(), action,
                    new KeyEvent(key.getValue(), 0, 0));
        }
    }

    /** A right click, as MouseHandler reports one: pressed, clicked, released. */
    private static void use(Minecraft minecraft) {
        InputConstants.Key key = ((KeyMappingAccessor) minecraft.options.keyUse).herzium$getBoundKey();
        KeyMapping.set(key, true);
        KeyMapping.click(key);
        KeyMapping.set(key, false);
    }

    private static void record(Minecraft minecraft) {
        Cycle cycle = plan.get(index);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", cycle.order().name());
        result.put("speed", cycle.speed().name());
        result.put("obsidianPlaced", obsidianNanos != 0L);
        result.put("crystalPlaced", crystalNanos != 0L);
        result.put("obsidianMsFromClick", obsidianNanos == 0L ? null : (obsidianNanos - actNanos[1]) / 1e6);
        result.put("crystalMsFromClick", crystalNanos == 0L ? null : (crystalNanos - actNanos[3]) / 1e6);
        result.put("hudMsFromKey", hudNanos == 0L ? null : (hudNanos - actNanos[0]) / 1e6);
        result.put("commitMsFromKey", commitNanos == 0L ? null : (commitNanos - actNanos[0]) / 1e6);
        result.put("handHeightAfterTick", tickHeightAfterPlace < 0F ? null : (double) tickHeightAfterPlace);
        result.put("handHeightRendered", renderHeightAfterPlace < 0F ? null : (double) renderHeightAfterPlace);
        result.put("finalSlot", minecraft.player.getInventory().getSelectedSlot() + 1);
        results.add(result);
    }

    private static void finish() throws Exception {
        HerziumConfig config = HerziumConfig.get();
        while (config.hotbarOrder() != HotbarOrder.HERZIUM) {
            config.cycleHotbarOrder();
        }
        StringBuilder json = new StringBuilder("[");
        for (Map<String, Object> result : results) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : result.entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                Object value = entry.getValue();
                json.append('"').append(entry.getKey()).append("\":")
                        .append(value instanceof String text ? "\"" + text + "\"" : String.valueOf(value));
            }
            json.append('}');
        }
        json.append(']');
        Files.writeString(Path.of(System.getProperty("herzium.orders.report")), json, StandardCharsets.UTF_8);
        System.out.println("[HERZIUM-SMOKE] Crystal validation finished: " + results.size() + " cycles");
    }
}
