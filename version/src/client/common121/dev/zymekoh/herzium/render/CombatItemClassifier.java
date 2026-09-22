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
 * 1.21.x port of the classifier in {@code src/client/java}.
 *
 * <p>It exists only because 1.21.x has no {@code ItemStack.typeHolder()}: the
 * tag questions have to go through {@code ItemStack.is(TagKey)} instead. Every
 * decision the shared class makes is reproduced here on purpose -- the per-item
 * cache, the "tags are not live yet" answer that preserves Vanilla, and the
 * {@link #invalidate()} hook the level-change check calls. An earlier copy of
 * this file had none of them, so the 1.21.x jars re-ran nine tag lookups per
 * hand per frame and kept answering from a cache that nothing ever cleared.</p>
 *
 * <p>{@code ItemTags.SPEARS} only exists from 1.21.11 on; the build strips that
 * line for older targets. Spears are covered by {@code WEAPON_ENCHANTABLE}
 * there, so nothing is misclassified either way.</p>
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

    /** Whether the item tag set has actually been populated. */
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
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SPEARS) // herzium:spears-tag
                || stack.is(ItemTags.WEAPON_ENCHANTABLE)
                || stack.is(ItemTags.MACE_ENCHANTABLE)
                || stack.is(ItemTags.BOW_ENCHANTABLE)
                || stack.is(ItemTags.CROSSBOW_ENCHANTABLE)
                || stack.is(ItemTags.TRIDENT_ENCHANTABLE)
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof ShieldItem;
    }
}
