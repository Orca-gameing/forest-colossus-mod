package com.forestcolossus.item;

import com.forestcolossus.ForestColossusMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {
    
    // Colossus Heart - rare drop, used for crafting
    public static final Item COLOSSUS_HEART = registerItem("colossus_heart",
        new Item(new Item.Settings()
            .maxCount(1)
            .rarity(Rarity.EPIC)
        ));
    
    // Ancient Bark - common drop, crafting material
    public static final Item ANCIENT_BARK = registerItem("ancient_bark",
        new Item(new Item.Settings()
            .maxCount(64)
            .rarity(Rarity.UNCOMMON)
        ));
    
    // Living Branch - weapon drop
    public static final Item LIVING_BRANCH = registerItem("living_branch",
        new LivingBranchItem(ToolMaterials.NETHERITE, new Item.Settings()
            .maxCount(1)
            .rarity(Rarity.RARE)
        ));
    
    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(ForestColossusMod.MOD_ID, name), item);
    }
    
    public static void registerItems() {
        ForestColossusMod.LOGGER.info("Registering items for " + ForestColossusMod.MOD_ID);
        
        // Add to creative tabs
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(LIVING_BRANCH);
        });
        
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(COLOSSUS_HEART);
            entries.add(ANCIENT_BARK);
        });
    }
}
