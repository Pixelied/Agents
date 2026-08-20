# Monumental throat that reveals the arena and physically cuts through its west wall.
# connector: arena_approach->arena
fill ~38 ~-18 ~61 ~49 ~-9 ~75 minecraft:stone_bricks
fill ~39 ~-17 ~62 ~49 ~-10 ~74 minecraft:air
fill ~39 ~-18 ~62 ~49 ~-18 ~74 minecraft:polished_deepslate
# Narrow sanctum end into a tall reveal chamber.
fill ~38 ~-17 ~64 ~40 ~-12 ~72 minecraft:air
fill ~40 ~-17 ~62 ~41 ~-10 ~64 minecraft:chiseled_stone_bricks
fill ~40 ~-17 ~72 ~41 ~-10 ~74 minecraft:chiseled_stone_bricks
fill ~44 ~-17 ~62 ~45 ~-9 ~64 minecraft:cracked_stone_bricks
fill ~44 ~-17 ~72 ~45 ~-9 ~74 minecraft:mossy_stone_bricks
fill ~48 ~-17 ~62 ~49 ~-9 ~64 minecraft:chiseled_stone_bricks
fill ~48 ~-17 ~72 ~49 ~-9 ~74 minecraft:chiseled_stone_bricks
# Ceiling ribs frame the first sightline to the prison.
fill ~40 ~-9 ~64 ~41 ~-9 ~72 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~44 ~-9 ~64 ~45 ~-9 ~72 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~48 ~-9 ~64 ~49 ~-9 ~72 minecraft:stone_brick_slab[type=top,waterlogged=false]
setblock ~41 ~-9 ~68 minecraft:soul_lantern[hanging=true]
setblock ~45 ~-9 ~68 minecraft:soul_lantern[hanging=true]
# Final seal only opens once all three dungeon puzzle flags are complete.
fill ~43 ~-17 ~64 ~43 ~-13 ~68 minecraft:iron_bars
setblock ~43 ~-12 ~66 minecraft:chiseled_stone_bricks
# Rubble / age at the threshold.
setblock ~42 ~-17 ~63 minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]
setblock ~43 ~-17 ~73 minecraft:stone_brick_slab[type=bottom,waterlogged=false]
setblock ~46 ~-17 ~63 minecraft:cracked_stone_bricks
setblock ~46 ~-17 ~73 minecraft:mossy_stone_bricks
setblock ~47 ~-17 ~65 minecraft:cobweb
# Carve the actual arena doorway LAST so arena wall construction cannot disconnect it.
fill ~47 ~-17 ~66 ~49 ~-11 ~70 minecraft:air
fill ~47 ~-18 ~66 ~49 ~-18 ~70 minecraft:polished_deepslate
fill ~47 ~-10 ~65 ~49 ~-10 ~71 minecraft:stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]
