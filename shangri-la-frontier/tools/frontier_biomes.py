"""The biomes of the designed Frontier map (tools/worldmap.py), in the order its grid indexes them.

Regions first, then the biomes painted under single sites: towns have no hostile spawns or trees,
and each landmark gets its own name for the "Now entering ..." title.
"""
REGIONS = ["frontier_sea", "frontier_shore", "fastera_plains", "rampant_forest", "nightslayer_woods", "diremarsh_waste",
           "prismatic_forest_grotto", "lightless_barrens", "thirdrema_highlands", "hidden_garden", "frontier_peaks",
           "rabbit_isle"]
SITES = ["firstia", "secondil", "thirdrema", "rabituza", "goblin_camp", "serpent_bridge", "nightslayer_den",
         "wezaemon_tomb", "empire_bee_hive", "mud_digger_lair", "shadow_altar", "divinity_ruins"]
BIOMES = REGIONS + SITES
