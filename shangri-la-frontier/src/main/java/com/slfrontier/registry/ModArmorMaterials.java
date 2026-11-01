package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;

/**
 * Armor materials. Their look comes from the equipment assets {@code slfrontier:<name>}
 * ({@code assets/slfrontier/equipment/}); repairs use the item tags {@code slfrontier:repairs/<name>}.
 */
public final class ModArmorMaterials {
    /** Sunraku's iconic blue bird mask. */
    public static final ArmorMaterial BIRD_MASK = new ArmorMaterial(12,
            Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 3, ArmorType.LEGGINGS, 2, ArmorType.BOOTS, 1, ArmorType.BODY, 3),
            15, SoundEvents.ARMOR_EQUIP_LEATHER, 0f, 0f, repairs("bird_mask"), asset("bird_mask"));

    /** The Battlehorn Helm, carved from a Quad Beetle shell. */
    public static final ArmorMaterial BATTLEHORN = new ArmorMaterial(30,
            Map.of(ArmorType.HELMET, 4, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 8),
            12, SoundEvents.ARMOR_EQUIP_TURTLE, 2f, 0.1f, repairs("battlehorn"), asset("battlehorn"));

    private ModArmorMaterials() {
    }

    private static TagKey<net.minecraft.world.item.Item> repairs(String name) {
        return TagKey.create(Registries.ITEM, SLFrontier.id("repairs/" + name));
    }

    private static ResourceKey<EquipmentAsset> asset(String name) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, SLFrontier.id(name));
    }
}
