# Cinematic ruined Gorgon temple. Instance anchor is the nave center.
# connector: surface->descent
fill ~-19 ~ ~-18 ~19 ~12 ~20 minecraft:air
fill ~-17 ~-3 ~-15 ~17 ~-2 ~17 minecraft:stone_bricks
fill ~-15 ~-1 ~-13 ~15 ~-1 ~15 minecraft:stone_bricks
fill ~-11 ~-1 ~-10 ~11 ~-1 ~11 minecraft:polished_andesite
fill ~-4 ~-1 ~-11 ~4 ~-1 ~12 minecraft:cracked_stone_bricks
fill ~-14 ~-1 ~-12 ~-10 ~-1 ~-8 minecraft:mossy_stone_bricks
fill ~10 ~-1 ~7 ~14 ~-1 ~12 minecraft:mossy_stone_bricks
fill ~-14 ~-1 ~4 ~-11 ~-1 ~8 minecraft:cracked_stone_bricks
fill ~11 ~-1 ~-8 ~14 ~-1 ~-4 minecraft:cracked_stone_bricks
# Broken perimeter walls leave the facade and sky exposed.
fill ~-16 ~ ~-14 ~-7 ~5 ~-13 minecraft:stone_bricks
fill ~7 ~ ~-14 ~16 ~5 ~-13 minecraft:stone_bricks
fill ~-16 ~ ~-12 ~-15 ~4 ~-3 minecraft:stone_bricks
fill ~-16 ~ ~4 ~-15 ~6 ~14 minecraft:stone_bricks
fill ~15 ~ ~-12 ~16 ~6 ~-5 minecraft:stone_bricks
fill ~15 ~ ~2 ~16 ~4 ~14 minecraft:stone_bricks
fill ~-14 ~ ~14 ~-5 ~5 ~15 minecraft:stone_bricks
fill ~5 ~ ~14 ~14 ~6 ~15 minecraft:stone_bricks
fill ~-6 ~ ~14 ~6 ~2 ~15 minecraft:cracked_stone_bricks
# Front broken arch.
fill ~-6 ~ ~-14 ~-5 ~8 ~-13 minecraft:chiseled_stone_bricks
fill ~5 ~ ~-14 ~6 ~8 ~-13 minecraft:chiseled_stone_bricks
fill ~-5 ~7 ~-14 ~5 ~8 ~-13 minecraft:stone_bricks
fill ~-3 ~7 ~-15 ~3 ~7 ~-12 minecraft:air
setblock ~-5 ~8 ~-14 minecraft:stone_brick_stairs[facing=east,half=top]
setblock ~5 ~8 ~-14 minecraft:stone_brick_stairs[facing=west,half=top]
setblock ~-4 ~8 ~-14 minecraft:cracked_stone_bricks
setblock ~4 ~8 ~-14 minecraft:mossy_stone_bricks
# Nave columns: intentionally uneven/broken.
fill ~-11 ~ ~-8 ~-10 ~7 ~-7 minecraft:chiseled_stone_bricks
fill ~10 ~ ~-8 ~11 ~5 ~-7 minecraft:chiseled_stone_bricks
fill ~-11 ~ ~-1 ~-10 ~5 ~ minecraft:chiseled_stone_bricks
fill ~10 ~ ~-1 ~11 ~8 ~ minecraft:chiseled_stone_bricks
fill ~-11 ~ ~6 ~-10 ~8 ~7 minecraft:chiseled_stone_bricks
fill ~10 ~ ~6 ~11 ~6 ~7 minecraft:chiseled_stone_bricks
fill ~-12 ~7 ~-9 ~-9 ~7 ~-6 minecraft:stone_brick_slab[type=top]
fill ~9 ~5 ~-9 ~12 ~5 ~-6 minecraft:stone_brick_slab[type=top]
fill ~-12 ~5 ~-2 ~-9 ~5 ~1 minecraft:cracked_stone_bricks
fill ~9 ~8 ~-2 ~12 ~8 ~1 minecraft:mossy_stone_bricks
fill ~-12 ~8 ~5 ~-9 ~8 ~8 minecraft:stone_brick_slab[type=top]
fill ~9 ~6 ~5 ~12 ~6 ~8 minecraft:stone_brick_slab[type=top]
# Low side shrines and serpent relief plinths.
fill ~-14 ~ ~-5 ~-12 ~2 ~-1 minecraft:mossy_stone_bricks
fill ~12 ~ ~-5 ~14 ~2 ~-1 minecraft:cracked_stone_bricks
setblock ~-13 ~3 ~-3 minecraft:chiseled_stone_bricks
setblock ~13 ~3 ~-3 minecraft:chiseled_stone_bricks
setblock ~-13 ~4 ~-3 minecraft:cobblestone_wall
setblock ~13 ~4 ~-3 minecraft:cobblestone_wall
fill ~-3 ~ ~4 ~3 ~1 ~8 minecraft:stone_bricks
fill ~-2 ~1 ~5 ~2 ~2 ~7 minecraft:chiseled_stone_bricks
fill ~-1 ~2 ~6 ~1 ~4 ~6 minecraft:cobblestone_wall
# Partial roof ribs create height without boxing the room in.
fill ~-13 ~9 ~-9 ~-9 ~9 ~10 minecraft:stone_brick_slab[type=top]
fill ~9 ~9 ~-9 ~13 ~9 ~10 minecraft:stone_brick_slab[type=top]
fill ~-9 ~10 ~-7 ~9 ~10 ~-5 minecraft:cracked_stone_bricks
fill ~-9 ~10 ~2 ~9 ~10 ~4 minecraft:mossy_stone_bricks
fill ~-5 ~10 ~-4 ~5 ~10 ~3 minecraft:air
# Rubble and age patches.
setblock ~-8 ~ ~-11 minecraft:stone_brick_stairs[facing=south,half=bottom]
setblock ~-7 ~ ~-10 minecraft:stone_brick_slab[type=bottom]
setblock ~8 ~ ~-5 minecraft:stone_brick_stairs[facing=west,half=bottom]
setblock ~9 ~ ~-4 minecraft:cobblestone_wall
setblock ~-12 ~ ~10 minecraft:stone_brick_slab[type=bottom]
setblock ~-11 ~ ~11 minecraft:moss_carpet
setblock ~12 ~ ~10 minecraft:stone_brick_stairs[facing=north,half=bottom]
setblock ~11 ~ ~11 minecraft:moss_carpet
setblock ~-6 ~ ~3 minecraft:gravel
setblock ~6 ~ ~2 minecraft:gravel
setblock ~-9 ~ ~8 minecraft:moss_carpet
setblock ~9 ~ ~8 minecraft:moss_carpet
# Lighting draws the player toward the rear descent.
setblock ~-6 ~4 ~-13 minecraft:soul_lantern[hanging=true]
setblock ~6 ~4 ~-13 minecraft:soul_lantern[hanging=true]
setblock ~-10 ~6 ~6 minecraft:soul_lantern[hanging=true]
setblock ~10 ~5 ~6 minecraft:soul_lantern[hanging=true]
setblock ~-3 ~3 ~10 minecraft:soul_lantern[hanging=true]
setblock ~3 ~3 ~10 minecraft:soul_lantern[hanging=true]
# Rear stair mouth physically overlaps build_descent at z=10..12.
fill ~-4 ~-1 ~9 ~4 ~3 ~12 minecraft:stone_bricks
fill ~-3 ~ ~9 ~3 ~3 ~12 minecraft:air
fill ~-3 ~-1 ~10 ~3 ~-1 ~12 minecraft:cracked_stone_bricks
setblock ~-4 ~1 ~10 minecraft:chiseled_stone_bricks
setblock ~4 ~1 ~10 minecraft:chiseled_stone_bricks
setblock ~-4 ~3 ~10 minecraft:cobblestone_wall
setblock ~4 ~3 ~10 minecraft:cobblestone_wall
