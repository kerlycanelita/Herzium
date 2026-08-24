package dev.zymekoh.herzium.render;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;

/** 1.21.x combat classifier; this branch predates the spear item tag. */
public final class CombatItemClassifier {
    private CombatItemClassifier() {
    }

    public static boolean preservesVanillaEquipTransition(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.WEAPON_ENCHANTABLE)
                || stack.is(ItemTags.MACE_ENCHANTABLE)
                || stack.is(ItemTags.BOW_ENCHANTABLE)
                || stack.is(ItemTags.CROSSBOW_ENCHANTABLE)
                || stack.is(ItemTags.TRIDENT_ENCHANTABLE)
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof ShieldItem;
    }
}
