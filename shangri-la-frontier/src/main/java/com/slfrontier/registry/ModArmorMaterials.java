package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, SLFrontier.MODID);

    /** Sunraku's iconic blue bird mask. */
    public static final Holder<ArmorMaterial> BIRD_MASK = ARMOR_MATERIALS.register("bird_mask", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                map.put(ArmorItem.Type.HELMET, 2);
                map.put(ArmorItem.Type.CHESTPLATE, 3);
                map.put(ArmorItem.Type.LEGGINGS, 2);
                map.put(ArmorItem.Type.BOOTS, 1);
                map.put(ArmorItem.Type.BODY, 3);
            }),
            15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.FEATHER),
            List.of(new ArmorMaterial.Layer(SLFrontier.id("bird_mask"))), 0f, 0f));

    /** The Battlehorn Helm, carved from a Quad Beetle shell. */
    public static final Holder<ArmorMaterial> BATTLEHORN = ARMOR_MATERIALS.register("battlehorn", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                map.put(ArmorItem.Type.HELMET, 4);
                map.put(ArmorItem.Type.CHESTPLATE, 8);
                map.put(ArmorItem.Type.LEGGINGS, 6);
                map.put(ArmorItem.Type.BOOTS, 3);
                map.put(ArmorItem.Type.BODY, 8);
            }),
            12, SoundEvents.ARMOR_EQUIP_TURTLE, () -> Ingredient.of(ModItems.QUAD_BEETLE_SHELL.get()),
            List.of(new ArmorMaterial.Layer(SLFrontier.id("battlehorn"))), 2f, 0.1f));

    private ModArmorMaterials() {
    }
}
