# Blind Passage — four watched floor zones teach timing and line-of-sight discipline.
# connector: blind->sanctum
fill ~-18 ~-18 ~57 ~-2 ~-10 ~69 minecraft:stone_bricks
fill ~-17 ~-17 ~58 ~-3 ~-11 ~68 minecraft:air
fill ~-17 ~-18 ~58 ~-3 ~-18 ~68 minecraft:polished_deepslate
# Entry arch from the labyrinth.
fill ~-18 ~-17 ~60 ~-17 ~-13 ~64 minecraft:air
fill ~-18 ~-18 ~59 ~-18 ~-10 ~59 minecraft:chiseled_stone_bricks
fill ~-18 ~-18 ~65 ~-18 ~-10 ~65 minecraft:chiseled_stone_bricks
fill ~-18 ~-11 ~59 ~-18 ~-11 ~65 minecraft:stone_brick_stairs[facing=south,half=top,shape=straight,waterlogged=false]
# Four clearly separated watched quadrants.
fill ~-14 ~-18 ~60 ~-12 ~-18 ~62 minecraft:cracked_deepslate_tiles
fill ~-10 ~-18 ~60 ~-8 ~-18 ~62 minecraft:cracked_deepslate_tiles
fill ~-14 ~-18 ~64 ~-12 ~-18 ~66 minecraft:cracked_deepslate_tiles
fill ~-10 ~-18 ~64 ~-8 ~-18 ~66 minecraft:cracked_deepslate_tiles
fill ~-15 ~-18 ~59 ~-11 ~-18 ~59 minecraft:stone_bricks
fill ~-11 ~-18 ~59 ~-7 ~-18 ~59 minecraft:mossy_stone_bricks
fill ~-15 ~-18 ~67 ~-11 ~-18 ~67 minecraft:mossy_stone_bricks
fill ~-11 ~-18 ~67 ~-7 ~-18 ~67 minecraft:stone_bricks
# Watcher faces and lamps make the puzzle readable from the doorway.
setblock ~-13 ~-16 ~60 minecraft:observer[facing=east]
setblock ~-9 ~-16 ~60 minecraft:observer[facing=east]
setblock ~-13 ~-16 ~64 minecraft:observer[facing=east]
setblock ~-9 ~-16 ~64 minecraft:observer[facing=east]
setblock ~-13 ~-15 ~60 minecraft:redstone_lamp[lit=false]
setblock ~-9 ~-15 ~60 minecraft:redstone_lamp[lit=false]
setblock ~-13 ~-15 ~64 minecraft:redstone_lamp[lit=false]
setblock ~-9 ~-15 ~64 minecraft:redstone_lamp[lit=false]
# Wall reliefs and vertical detail.
fill ~-16 ~-17 ~58 ~-16 ~-12 ~58 minecraft:chiseled_stone_bricks
fill ~-4 ~-17 ~58 ~-4 ~-12 ~58 minecraft:chiseled_stone_bricks
fill ~-16 ~-17 ~68 ~-16 ~-12 ~68 minecraft:cracked_stone_bricks
fill ~-4 ~-17 ~68 ~-4 ~-12 ~68 minecraft:mossy_stone_bricks
setblock ~-16 ~-11 ~58 minecraft:soul_lantern[hanging=true]
setblock ~-4 ~-11 ~58 minecraft:soul_lantern[hanging=true]
setblock ~-16 ~-11 ~68 minecraft:soul_lantern[hanging=true]
setblock ~-4 ~-11 ~68 minecraft:soul_lantern[hanging=true]
setblock ~-15 ~-17 ~68 minecraft:cobweb
setblock ~-5 ~-17 ~58 minecraft:moss_carpet
# Safe start marker and destination dais.
fill ~-16 ~-18 ~61 ~-15 ~-18 ~63 minecraft:chiseled_deepslate
fill ~-6 ~-18 ~60 ~-3 ~-18 ~64 minecraft:chiseled_deepslate
setblock ~-4 ~-17 ~62 minecraft:lodestone
# East gate opens only after the player crosses the watched zones.
fill ~-2 ~-17 ~60 ~-2 ~-13 ~64 minecraft:iron_bars
fill ~-2 ~-12 ~59 ~-2 ~-12 ~65 minecraft:stone_brick_slab[type=top,waterlogged=false]
