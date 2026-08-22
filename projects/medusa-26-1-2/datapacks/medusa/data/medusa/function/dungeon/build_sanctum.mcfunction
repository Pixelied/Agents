# Tall serpent sanctum attached to the southeast maze exit at logical cell (12,12).
# connector: maze->sanctum
# Carve the far-cell east portal through visible wall and hidden containment.
fill ~43 ~-17 ~113 ~46 ~-10 ~115 minecraft:air
fill ~43 ~-18 ~113 ~47 ~-18 ~115 minecraft:polished_deepslate
# Main sanctum shell and tall interior.
fill ~44 ~-18 ~104 ~62 ~-2 ~124 minecraft:stone_bricks
fill ~45 ~-17 ~105 ~61 ~-5 ~123 minecraft:air
fill ~45 ~-18 ~105 ~61 ~-18 ~123 minecraft:deepslate_tiles
fill ~48 ~-18 ~108 ~58 ~-18 ~120 minecraft:cracked_deepslate_tiles
# Massive serpent ribs and altar columns.
fill ~46 ~-17 ~106 ~47 ~-5 ~108 minecraft:chiseled_stone_bricks
fill ~59 ~-17 ~106 ~60 ~-5 ~108 minecraft:chiseled_stone_bricks
fill ~46 ~-17 ~120 ~47 ~-5 ~122 minecraft:mossy_stone_bricks
fill ~59 ~-17 ~120 ~60 ~-5 ~122 minecraft:cracked_stone_bricks
fill ~50 ~-17 ~111 ~52 ~-15 ~113 minecraft:chiseled_deepslate
fill ~54 ~-17 ~116 ~56 ~-15 ~118 minecraft:chiseled_deepslate
setblock ~51 ~-14 ~112 minecraft:wither_skeleton_skull[rotation=4]
setblock ~55 ~-14 ~117 minecraft:skeleton_skull[rotation=12]
# Layered vaulted roof: heavy side shoulders, inward stairs, and a raised central spine.
fill ~44 ~-4 ~104 ~62 ~-3 ~124 minecraft:deepslate_bricks
fill ~46 ~-4 ~106 ~60 ~-3 ~122 minecraft:air
fill ~46 ~-4 ~106 ~46 ~-3 ~122 minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]
fill ~60 ~-4 ~106 ~60 ~-3 ~122 minecraft:stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]
fill ~47 ~-3 ~106 ~59 ~-3 ~122 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~50 ~-2 ~109 ~56 ~-2 ~119 minecraft:polished_deepslate
setblock ~53 ~-4 ~110 minecraft:chain[axis=y,waterlogged=false]
setblock ~53 ~-5 ~110 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~53 ~-4 ~118 minecraft:chain[axis=y,waterlogged=false]
setblock ~53 ~-5 ~118 minecraft:soul_lantern[hanging=true,waterlogged=false]
# Corruption and green Gorgon accents strengthen toward the arena route.
setblock ~47 ~-17 ~109 minecraft:moss_carpet
setblock ~59 ~-17 ~118 minecraft:moss_carpet
setblock ~49 ~-16 ~122 minecraft:cobweb
setblock ~58 ~-16 ~107 minecraft:cobweb
fill ~51 ~-16 ~121 ~55 ~-13 ~121 minecraft:oxidized_cut_copper
setblock ~52 ~-14 ~120 minecraft:green_glazed_terracotta[facing=north]
setblock ~54 ~-14 ~120 minecraft:green_glazed_terracotta[facing=north]
# North throat into the roofed arena approach.
fill ~48 ~-17 ~103 ~54 ~-8 ~106 minecraft:air
fill ~49 ~-18 ~103 ~53 ~-18 ~106 minecraft:polished_deepslate
