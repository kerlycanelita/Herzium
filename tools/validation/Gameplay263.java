package herzium.validation;

import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.input.HotbarOrder;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import dev.zymekoh.herzium.mixin.KeyMappingAccessor;
import dev.zymekoh.herzium.render.CombatItemClassifier;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Real 26.3 client/world integration checks, packaged only in the validation mod. */
public final class Gameplay263 {
    private static boolean creating;
    private static int worldFrames;
    private static int checks;

    public static void start(Object client) {
        Minecraft minecraft = (Minecraft) client;
        CreateWorldScreen.testWorld(minecraft, () -> {});
    }

    public static boolean tick(Object client) throws Exception {
        Minecraft minecraft = (Minecraft) client;
        if (!creating && minecraft.gui.screen() instanceof CreateWorldScreen screen) {
            screen.getUiState().setName("Herzium isolated validation");
            screen.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            screen.getUiState().setAllowCommands(true);
            creating = true;
            Method create = CreateWorldScreen.class.getDeclaredMethod("onCreate");
            create.setAccessible(true);
            create.invoke(screen);
        }
        // 26.3 asks before creating a world whose settings are not marked stable (the
        // experimental-settings warning); the validation world is disposable.
        if (creating && minecraft.player == null && minecraft.gui.screen() instanceof ConfirmScreen confirm) {
            Field callback = ConfirmScreen.class.getDeclaredField("callback");
            callback.setAccessible(true);
            ((BooleanConsumer) callback.get(confirm)).accept(true);
            return false;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null) return false;
        if (++worldFrames < 120) return false;
        LocalPlayer player = minecraft.player;
        Inventory inventory = player.getInventory();
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack shield = new ItemStack(Items.SHIELD);
        check(!CombatItemClassifier.preservesVanillaEquipTransition(stone), "live tags classify stone as ordinary");
        check(CombatItemClassifier.preservesVanillaEquipTransition(sword), "sword keeps vanilla transition");
        check(CombatItemClassifier.preservesVanillaEquipTransition(shield), "shield keeps vanilla transition");

        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, new ItemStack(Items.STONE));
        Method handle = Minecraft.class.getDeclaredMethod("handleKeybinds");
        handle.setAccessible(true);
        for (HotbarOrder order : HotbarOrder.values()) {
            while (HerziumConfig.get().hotbarOrder() != order) HerziumConfig.get().cycleHotbarOrder();
            KeyMapping.click(((KeyMappingAccessor) minecraft.options.keyHotbarSlots[8]).herzium$getBoundKey());
            KeyMapping.click(((KeyMappingAccessor) minecraft.options.keyHotbarSlots[0]).herzium$getBoundKey());
            handle.invoke(minecraft);
            check(inventory.getSelectedSlot() == (order == HotbarOrder.VANILLA ? 8 : 0), "real slot pass: " + order);
            for (KeyMapping key : minecraft.options.keyHotbarSlots) {
                check(((KeyMappingAccessor) key).herzium$getPendingClickCount() == 0, "click queue consumed once");
            }
        }
        while (HerziumConfig.get().hotbarOrder() != HotbarOrder.HERZIUM) HerziumConfig.get().cycleHotbarOrder();
        ImmediateHotbarInput.clearPreview();
        inventory.setSelectedSlot(0);
        inventory.setItem(0, stone);
        FirstPersonHandsAndItems hands = player.firstPersonHandsAndItems();
        FirstPersonHandsAndItemsRenderState state = new FirstPersonHandsAndItemsRenderState();
        hands.extractRenderState(player, 0.5F, state);
        check(state.mainHandItem == stone, "ordinary stack extracted before model resolution");
        check(!state.mainHandRenderState.isEmpty(), "ordinary model resolved");
        check(state.mainHandHeight == 1.0F && state.oldMainHandHeight == 1.0F, "ordinary equip dip removed");

        inventory.setItem(0, sword);
        inventory.setItem(40, shield);
        set(hands, "mainHandItem", sword);
        set(hands, "offHandItem", shield);
        set(hands, "mainHandHeight", 0.35F);
        set(hands, "oMainHandHeight", 0.25F);
        set(hands, "offHandHeight", 0.45F);
        set(hands, "oOffHandHeight", 0.15F);
        hands.extractRenderState(player, 0.5F, state);
        check(state.mainHandItem == sword && state.offHandItem == shield, "combat stacks preserved");
        check(!state.mainHandRenderState.isEmpty() && !state.offHandRenderState.isEmpty(), "combat models resolved");
        check(state.mainHandHeight == 0.35F && state.oldMainHandHeight == 0.25F, "sword heights preserved");
        check(state.offHandHeight == 0.45F && state.oldOffHandHeight == 0.15F, "shield heights preserved");

        inventory.setItem(0, stone);
        inventory.setItem(40, new ItemStack(Items.BREAD));
        boolean busy = player.isHandsBusy();
        try {
            set(player, "handsBusy", true);
            hands.extractRenderState(player, 0.5F, state);
            check(state.mainHandItem == stone, "busy ordinary stack still synchronized");
            check(state.mainHandHeight == 0.35F && state.oldMainHandHeight == 0.25F, "busy main hand lowering preserved");
            check(state.offHandHeight == 0.45F && state.oldOffHandHeight == 0.15F, "busy offhand lowering preserved");
        } finally {
            set(player, "handsBusy", busy);
        }
        System.out.println("[HERZIUM-SMOKE] Gameplay PASS: " + checks + " live world/input/hand assertions");
        return true;
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
