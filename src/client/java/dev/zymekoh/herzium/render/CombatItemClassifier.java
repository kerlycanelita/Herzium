package dev.zymekoh.herzium.render;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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
 *
 * <p>Clearing on level change is not enough on its own. The client can render
 * frames after the level exists but before the server's tag packet arrives, and
 * in that window every tag lookup answers "no" -- so a sword would be
 * classified as an ordinary item and that wrong answer would be cached for the
 * whole session, permanently costing swords their Vanilla equip animation on
 * servers. Two things prevent it: an unanswerable classification reports
 * {@code true}, which preserves Vanilla, and nothing is written to the cache
 * until the tag set is demonstrably live.</p>
 */
public final class CombatItemClassifier {
    private static final ConcurrentMap<Item, Boolean> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean tagsLive;

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

        // Until tags are live every lookup answers "no", which would make a
        // sword look ordinary. Herzium's rule is that a combat item keeps
        // Vanilla's transition, so the safe answer to "I cannot tell yet" is
        // the one that changes nothing.
        if (!tagsAreLive()) {
            return true;
        }

        boolean combat = classify(stack);
        CACHE.put(item, combat);
        return combat;
    }

    /**
     * Whether the item tag set has actually been populated.
     *
     * <p>Probes a single item that vanilla always tags. Before the server's tag
     * packet arrives this answers {@code false}; afterwards it latches, so the
     * cost is one holder check per call for the first moments of a session and
     * nothing at all after that. A datapack that untags diamond swords would
     * keep Herzium uncached and correct rather than cached and wrong.</p>
     */
    private static boolean tagsAreLive() {
        if (tagsLive) {
            return true;
        }

        boolean live = Items.DIAMOND_SWORD.builtInRegistryHolder().is(ItemTags.SWORDS);
        if (live) {
            tagsLive = true;
        }
        return live;
    }

    /** Called when the client enters a level, because that is when tags sync. */
    public static void invalidate() {
        CACHE.clear();
        tagsLive = false;
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
