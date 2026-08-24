package dev.zymekoh.herzium.render;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;

/**
 * Identifies combat-capable stacks whose Vanilla equip transition must remain
 * visible.
 *
 * <p>The answer is cached per {@link Item}. Uncached, this ran nine tag lookups
 * for each hand on every rendered frame -- eighteen per frame, which at the
 * frame rates this mod exists to reach is tens of thousands of set lookups a
 * second, forever, to re-derive an answer that only changes when the server
 * sends new tags. The cache turns that into one hash lookup and allocates
 * nothing after an item is seen once.</p>
 *
 * <p>Item tags arrive with the server's tag sync on join, so the cache is
 * cleared whenever the client enters a different level (see
 * {@code MinecraftMixin}). A datapack reload that retags items mid-session
 * without a rejoin would leave a stale entry; the visible consequence is that
 * one item keeps or loses its equip dip until the next join, which is not worth
 * a per-frame invalidation check to prevent.</p>
 */
public final class CombatItemClassifier {
    private static final ConcurrentMap<Item, Boolean> CACHE = new ConcurrentHashMap<>();

    private CombatItemClassifier() {
    }

    public static boolean preservesVanillaEquipTransition(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        Boolean cached = CACHE.get(item);
        if (cached != null) {
            return cached;
        }

        boolean combat = classify(stack);
        CACHE.put(item, combat);
        return combat;
    }

    /** Called when the client enters a level, because that is when tags sync. */
    public static void invalidate() {
        CACHE.clear();
    }

    private static boolean classify(ItemStack stack) {
        return stack.typeHolder().is(ItemTags.SWORDS)
                || stack.typeHolder().is(ItemTags.AXES)
                || stack.typeHolder().is(ItemTags.PICKAXES)
                || stack.typeHolder().is(ItemTags.SPEARS)
                || stack.typeHolder().is(ItemTags.WEAPON_ENCHANTABLE)
                || stack.typeHolder().is(ItemTags.MACE_ENCHANTABLE)
                || stack.typeHolder().is(ItemTags.BOW_ENCHANTABLE)
                || stack.typeHolder().is(ItemTags.CROSSBOW_ENCHANTABLE)
                || stack.typeHolder().is(ItemTags.TRIDENT_ENCHANTABLE)
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof ShieldItem;
    }
}
