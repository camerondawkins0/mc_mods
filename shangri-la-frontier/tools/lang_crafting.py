"""English text for Frontier crafting: stations, the crafting screen, new items and messages.

Merged into the lang file by lang.build().
"""

ITEMS = {
    "frontier_herb": ("Frontier Herb", "A hardy herb that grows in the Frontier's grass. The base of most remedies."),
    "gray_steel_ingot": ("Gray Steel Ingot", "Gray Ore, smelted at a forge and folded with coal."),
    "leather_grip": ("Leather Grip", "Wrapped hide for a sure hold on any hilt."),
    "whetstone": ("Whetstone", "Use with a damaged weapon or tool in your other hand to sharpen it."),
    "healing_potion": ("Healing Potion", "A bitter red brew. Closes wounds in a heartbeat."),
    "greater_healing_potion": ("Greater Healing Potion", "Prismatic dust makes the cure linger."),
    "stamina_draught": ("Stamina Draught", "One gulp and your legs remember how to run."),
    "vigor_elixir": ("Vigor Elixir", "Honeyed and shimmering. You feel like you could sprint across the Frontier."),
    "antidote": ("Antidote", "Brewed from venom itself."),
    "nightsight_tonic": ("Nightsight Tonic", "Lets your eyes drink the dark, like the Nightslayer's fur."),
    "ironhide_tonic": ("Ironhide Tonic", "Your skin hardens like a Quad Beetle's shell."),
    "bandage": ("Bandage", "Herb-soaked cloth. Wrap a wound and catch your breath."),
    "trail_ration": ("Trail Ration", "Bread, meat and herbs, packed for the road."),
}

BLOCKS = {
    "frontier_forge": "Frontier Forge",
    "alchemy_table": "Alchemy Table",
    "frontier_workbench": "Frontier Workbench",
}

TAGS = {
    "hides": "Any Hide",
    "cooked_meats": "Any Cooked Meat",
    "crystal_shards": "Any Crystal Shard",
    "venoms": "Any Venom",
    "glowing_reagents": "Any Glowing Reagent",
}

STATIONS = {
    "forge": "Forge",
    "alchemy": "Alchemy Table",
    "workbench": "Workbench",
}

CATEGORIES = {
    "weapon": ("Weapons", "Weapon"),
    "armor": ("Armor", "Armor"),
    "consumable": ("Consumables", "Consumable"),
    "material": ("Materials", "Material"),
    "gear": ("Gear", "Gear"),
}

GUI = {
    "gui.slfrontier.craft.tab.all": "All",
    "gui.slfrontier.craft.header_here": "%s",
    "gui.slfrontier.craft.header_other": "Other stations",
    "gui.slfrontier.craft.run_by": "· run by %s",
    "gui.slfrontier.craft.level": "Lv.%s",
    "gui.slfrontier.craft.req_level": "Requires Lv.%s",
    "gui.slfrontier.craft.materials": "MATERIALS",
    "gui.slfrontier.craft.max": "Max",
    "gui.slfrontier.craft.select": "Select a recipe.",
    "gui.slfrontier.craft.empty": "No recipes in this category.",
    "gui.slfrontier.craft.no_recipes": "No Frontier recipes are loaded. Is the mod's data pack enabled?",
    "gui.slfrontier.craft.ready": "Ready · materials for %s",
    "gui.slfrontier.craft.missing": "Missing materials",
    "gui.slfrontier.craft.batch_time": "%s× · takes %ss",
    "gui.slfrontier.craft.lock.station": "Made at the %s",
    "gui.slfrontier.craft.lock.npc": "Only %s can make this",
    "gui.slfrontier.craft.lock.stage": "Unlocks later in the story (Act %s)",
    "gui.slfrontier.craft.lock.level": "Requires Lv.%s",
    "gui.slfrontier.craft.action.forge": "Forge",
    "gui.slfrontier.craft.action.alchemy": "Brew",
    "gui.slfrontier.craft.action.workbench": "Craft",
    "gui.slfrontier.craft.cancel": "Cancel",
    "gui.slfrontier.craft.progress.forge": "Forging %s ×%s...",
    "gui.slfrontier.craft.progress.alchemy": "Brewing %s ×%s...",
    "gui.slfrontier.craft.progress.workbench": "Crafting %s ×%s...",
}

MESSAGES = {
    "message.slfrontier.craft.done": "Crafted %s× %s",
    "message.slfrontier.craft.too_far": "You moved away from the station.",
    "message.slfrontier.craft.unknown": "That recipe no longer exists.",
    "message.slfrontier.craft.missing": "You don't have the materials for that.",
    "message.slfrontier.craft.busy": "You're already crafting something.",
    "message.slfrontier.craft.cancelled": "Crafting cancelled.",
    "message.slfrontier.whetstone.nothing": "Hold a damaged weapon or tool in your other hand.",
    "message.slfrontier.whetstone.done": "Sharpened %s.",
    "tooltip.slfrontier.heals": "Restores %s ❤",
    "tooltip.slfrontier.stamina_full": "Refills your stamina",
    "tooltip.slfrontier.stamina": "Restores %s stamina",
    "tooltip.slfrontier.cures": "Cures %s",
    "dialogue.slfrontier.option.open_forge": "[Forge] Let's make something.",
}


def strings():
    out = dict(GUI)
    out.update(MESSAGES)
    for k, (name, lore) in ITEMS.items():
        out[f"item.slfrontier.{k}"] = name
        out[f"item.slfrontier.{k}.lore"] = lore
    for k, name in BLOCKS.items():
        out[f"block.slfrontier.{k}"] = name
    for k, name in TAGS.items():
        out[f"tag.item.slfrontier.{k}"] = name
    for k, name in STATIONS.items():
        out[f"station.slfrontier.{k}"] = name
    for k, (tab, single) in CATEGORIES.items():
        out[f"gui.slfrontier.craft.tab.{k}"] = tab
        out[f"gui.slfrontier.craft.cat.{k}"] = single
    return out


STRINGS = strings()
