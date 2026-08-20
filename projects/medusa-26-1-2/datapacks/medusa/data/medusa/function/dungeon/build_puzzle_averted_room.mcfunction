# Averted Eyes — three stone sentinels must be turned away from the Gorgon relief.
# connector: averted->labyrinth
fill ~-12 ~-18 ~28 ~9 ~-10 ~42 minecraft:stone_bricks
fill ~-11 ~-17 ~29 ~8 ~-11 ~41 minecraft:air
fill ~-11 ~-18 ~29 ~8 ~-18 ~41 minecraft:polished_deepslate
fill ~-10 ~-18 ~30 ~7 ~-18 ~31 minecraft:cracked_deepslate_tiles
fill ~-10 ~-18 ~39 ~7 ~-18 ~40 minecraft:cracked_deepslate_tiles
# North doorway overlaps the descent landing.
fill ~-3 ~-17 ~28 ~3 ~-12 ~30 minecraft:air
fill ~-4 ~-17 ~28 ~-4 ~-12 ~30 minecraft:chiseled_stone_bricks
fill ~4 ~-17 ~28 ~4 ~-12 ~30 minecraft:chiseled_stone_bricks
# Gorgon relief wall and eye-like lanterns.
fill ~-10 ~-16 ~31 ~6 ~-12 ~31 minecraft:mossy_stone_bricks
fill ~-7 ~-15 ~30 ~3 ~-12 ~31 minecraft:chiseled_stone_bricks
fill ~-5 ~-14 ~29 ~-4 ~-13 ~29 minecraft:cracked_stone_bricks
fill ~0 ~-14 ~29 ~1 ~-13 ~29 minecraft:cracked_stone_bricks
setblock ~-5 ~-12 ~30 minecraft:soul_lantern[hanging=true]
setblock ~1 ~-12 ~30 minecraft:soul_lantern[hanging=true]
# Three distinct sentinel plinths.
fill ~-9 ~-18 ~33 ~-7 ~-17 ~35 minecraft:stone_bricks
fill ~-5 ~-18 ~33 ~-3 ~-17 ~35 minecraft:stone_bricks
fill ~-1 ~-18 ~33 ~1 ~-17 ~35 minecraft:stone_bricks
setblock ~-8 ~-16 ~33 minecraft:stone_brick_stairs[facing=north,half=bottom]
setblock ~-4 ~-16 ~33 minecraft:stone_brick_stairs[facing=north,half=bottom]
setblock ~0 ~-16 ~33 minecraft:stone_brick_stairs[facing=north,half=bottom]
setblock ~-8 ~-17 ~35 minecraft:stone_button[face=floor,facing=north,powered=false]
setblock ~-4 ~-17 ~35 minecraft:stone_button[face=floor,facing=north,powered=false]
setblock ~0 ~-17 ~35 minecraft:stone_button[face=floor,facing=north,powered=false]
# Submit altar is deliberately separated from the rotation controls.
fill ~-6 ~-18 ~37 ~-2 ~-17 ~39 minecraft:chiseled_stone_bricks
setblock ~-4 ~-17 ~39 minecraft:stone_button[face=floor,facing=north,powered=false]
setblock ~-4 ~-15 ~38 minecraft:cobblestone_wall
setblock ~-4 ~-14 ~38 minecraft:soul_lantern
# Side columns/age detail make this a chamber, not maze furniture.
fill ~-11 ~-17 ~32 ~-10 ~-11 ~33 minecraft:cracked_stone_bricks
fill ~7 ~-17 ~32 ~8 ~-11 ~33 minecraft:mossy_stone_bricks
fill ~-11 ~-17 ~37 ~-10 ~-13 ~38 minecraft:mossy_stone_bricks
fill ~7 ~-17 ~37 ~8 ~-14 ~38 minecraft:cracked_stone_bricks
setblock ~-10 ~-17 ~36 minecraft:cobweb
setblock ~7 ~-17 ~36 minecraft:moss_carpet
setblock ~-9 ~-12 ~40 minecraft:soul_lantern[hanging=true]
setblock ~6 ~-12 ~40 minecraft:soul_lantern[hanging=true]
# South-east exit physically enters the generated labyrinth at x=3..8,z=41..43.
fill ~3 ~-17 ~40 ~8 ~-12 ~42 minecraft:air
fill ~3 ~-17 ~41 ~7 ~-13 ~41 minecraft:iron_bars
