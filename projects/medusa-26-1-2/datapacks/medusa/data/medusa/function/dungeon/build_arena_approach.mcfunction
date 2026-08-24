# Roofed monumental northbound approach from the relocated sanctum into Medusa's existing arena.
# connector: sanctum->arena
# Build after the arena so this corridor cleanly cuts through the south arena wall.
fill ~46 ~-18 ~86 ~54 ~-4 ~106 minecraft:stone_bricks
fill ~47 ~-17 ~87 ~53 ~-7 ~105 minecraft:air
fill ~47 ~-18 ~87 ~53 ~-18 ~105 minecraft:polished_deepslate
# Ribbed vaulted ceiling and procession columns.
fill ~46 ~-6 ~87 ~46 ~-4 ~105 minecraft:deepslate_bricks
fill ~54 ~-6 ~87 ~54 ~-4 ~105 minecraft:deepslate_bricks
fill ~47 ~-6 ~87 ~47 ~-6 ~105 minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]
fill ~53 ~-6 ~87 ~53 ~-6 ~105 minecraft:stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]
fill ~48 ~-5 ~87 ~52 ~-5 ~105 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~48 ~-17 ~90 ~49 ~-8 ~91 minecraft:chiseled_stone_bricks
fill ~52 ~-17 ~90 ~53 ~-8 ~91 minecraft:chiseled_stone_bricks
fill ~48 ~-17 ~96 ~49 ~-8 ~97 minecraft:cracked_stone_bricks
fill ~52 ~-17 ~96 ~53 ~-8 ~97 minecraft:mossy_stone_bricks
fill ~48 ~-17 ~102 ~49 ~-8 ~103 minecraft:chiseled_stone_bricks
fill ~52 ~-17 ~102 ~53 ~-8 ~103 minecraft:chiseled_stone_bricks
setblock ~50 ~-6 ~91 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~50 ~-6 ~97 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~50 ~-6 ~103 minecraft:soul_lantern[hanging=true,waterlogged=false]
# Carved serpent track and rubble make the approach feel ancient rather than boxy.
fill ~49 ~-18 ~88 ~51 ~-18 ~104 minecraft:cracked_deepslate_tiles
setblock ~48 ~-17 ~94 minecraft:cobweb
setblock ~52 ~-17 ~100 minecraft:cobweb
setblock ~48 ~-17 ~104 minecraft:moss_carpet
# Arena reveal: carve through the south-west wall into the existing chamber without moving boss coordinates.
fill ~48 ~-17 ~86 ~52 ~-8 ~90 minecraft:air
fill ~48 ~-18 ~86 ~52 ~-18 ~90 minecraft:polished_deepslate
