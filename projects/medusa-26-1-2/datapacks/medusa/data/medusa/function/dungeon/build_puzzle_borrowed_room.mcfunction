# Borrowed Gaze — align the two bronze sight-lines so the reflected gaze crosses the central idol.
# connector: borrowed->blind
fill ~-32 ~-18 ~43 ~-18 ~-10 ~56 minecraft:stone_bricks
fill ~-31 ~-17 ~44 ~-19 ~-11 ~55 minecraft:air
fill ~-31 ~-18 ~44 ~-19 ~-18 ~55 minecraft:polished_deepslate
fill ~-30 ~-18 ~45 ~-20 ~-18 ~46 minecraft:oxidized_cut_copper
fill ~-30 ~-18 ~53 ~-20 ~-18 ~54 minecraft:cut_copper
# West entry from the maze.
fill ~-32 ~-17 ~47 ~-30 ~-12 ~51 minecraft:air
fill ~-31 ~-17 ~46 ~-30 ~-11 ~46 minecraft:chiseled_stone_bricks
# Central idol and sight-line backdrop.
fill ~-26 ~-17 ~46 ~-24 ~-12 ~48 minecraft:chiseled_deepslate
setblock ~-25 ~-11 ~47 minecraft:soul_lantern[hanging=true]
setblock ~-25 ~-15 ~47 minecraft:lodestone
fill ~-29 ~-16 ~48 ~-21 ~-16 ~48 minecraft:copper_grate
# Left and right bronze mirror pedestals.
fill ~-30 ~-18 ~49 ~-27 ~-17 ~52 minecraft:oxidized_cut_copper
fill ~-23 ~-18 ~49 ~-20 ~-17 ~52 minecraft:weathered_cut_copper
setblock ~-28 ~-15 ~49 minecraft:lightning_rod[facing=north,waterlogged=false]
setblock ~-22 ~-15 ~49 minecraft:lightning_rod[facing=north,waterlogged=false]
setblock ~-28 ~-17 ~52 minecraft:stone_button[face=floor,facing=north,powered=false]
setblock ~-22 ~-17 ~52 minecraft:stone_button[face=floor,facing=north,powered=false]
# Framing columns and visual symmetry.
fill ~-31 ~-17 ~44 ~-30 ~-11 ~45 minecraft:mossy_stone_bricks
fill ~-20 ~-17 ~44 ~-19 ~-11 ~45 minecraft:cracked_stone_bricks
fill ~-31 ~-17 ~53 ~-30 ~-12 ~54 minecraft:cracked_stone_bricks
fill ~-20 ~-17 ~53 ~-19 ~-12 ~54 minecraft:mossy_stone_bricks
setblock ~-30 ~-12 ~50 minecraft:soul_lantern[hanging=true]
setblock ~-20 ~-12 ~50 minecraft:soul_lantern[hanging=true]
setblock ~-29 ~-17 ~47 minecraft:moss_carpet
setblock ~-21 ~-17 ~47 minecraft:moss_carpet
setblock ~-30 ~-17 ~54 minecraft:cobweb
# Ceiling ribs point toward the center target.
fill ~-30 ~-11 ~47 ~-27 ~-11 ~53 minecraft:stone_brick_slab[type=top]
fill ~-23 ~-11 ~47 ~-20 ~-11 ~53 minecraft:stone_brick_slab[type=top]
fill ~-27 ~-11 ~47 ~-23 ~-11 ~48 minecraft:cracked_stone_bricks
# South exit remains barred until the mirror alignment is solved.
fill ~-27 ~-17 ~54 ~-23 ~-12 ~56 minecraft:air
fill ~-27 ~-17 ~55 ~-23 ~-13 ~55 minecraft:iron_bars
