package dev.zymekoh.herzium.render;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;

/** Identifies combat-capable stacks whose Vanilla equip transition must remain visible. */
public final class CombatItemClassifier {
    private CombatItemClassifier() {
    }

    public static boolean preservesVanillaEquipTransition(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

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
