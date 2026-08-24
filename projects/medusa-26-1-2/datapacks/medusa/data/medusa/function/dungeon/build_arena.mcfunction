# Cinematic Medusa arena centered at ~64 ~-18 ~72. Backend coordinates intentionally preserved.
# Clear the volume before rebuilding it as a stepped/octagonal chamber.
fill ~47 ~-18 ~55 ~81 ~9 ~89 minecraft:air
# Layered floor and octagonal visual rings.
fill ~48 ~-18 ~56 ~80 ~-18 ~88 minecraft:polished_deepslate
fill ~52 ~-18 ~56 ~76 ~-18 ~88 minecraft:deepslate_tiles
fill ~48 ~-18 ~60 ~80 ~-18 ~84 minecraft:deepslate_tiles
fill ~54 ~-18 ~60 ~74 ~-18 ~84 minecraft:polished_deepslate
fill ~52 ~-18 ~62 ~76 ~-18 ~82 minecraft:cracked_deepslate_tiles
fill ~58 ~-18 ~62 ~70 ~-18 ~82 minecraft:polished_deepslate
# Cut stepped corners so the room reads as octagonal rather than a box.
fill ~48 ~-18 ~56 ~51 ~-18 ~59 minecraft:stone_bricks
fill ~77 ~-18 ~56 ~80 ~-18 ~59 minecraft:stone_bricks
fill ~48 ~-18 ~85 ~51 ~-18 ~88 minecraft:stone_bricks
fill ~77 ~-18 ~85 ~80 ~-18 ~88 minecraft:stone_bricks
# Outer stepped walls.
fill ~52 ~-17 ~55 ~76 ~5 ~56 minecraft:stone_bricks
fill ~52 ~-17 ~88 ~76 ~5 ~89 minecraft:stone_bricks
fill ~47 ~-17 ~60 ~48 ~5 ~84 minecraft:stone_bricks
fill ~80 ~-17 ~60 ~81 ~5 ~84 minecraft:stone_bricks
fill ~48 ~-17 ~57 ~51 ~5 ~60 minecraft:stone_bricks
fill ~77 ~-17 ~57 ~80 ~5 ~60 minecraft:stone_bricks
fill ~48 ~-17 ~84 ~51 ~5 ~87 minecraft:stone_bricks
fill ~77 ~-17 ~84 ~80 ~5 ~87 minecraft:stone_bricks
# Cracked and mossy wall panels break repetition.
fill ~57 ~-15 ~55 ~61 ~0 ~56 minecraft:cracked_stone_bricks
fill ~67 ~-15 ~55 ~71 ~0 ~56 minecraft:mossy_stone_bricks
fill ~57 ~-15 ~88 ~61 ~0 ~89 minecraft:mossy_stone_bricks
fill ~67 ~-15 ~88 ~71 ~0 ~89 minecraft:cracked_stone_bricks
fill ~47 ~-15 ~65 ~48 ~0 ~69 minecraft:mossy_stone_bricks
fill ~47 ~-15 ~75 ~48 ~0 ~79 minecraft:cracked_stone_bricks
fill ~80 ~-15 ~65 ~81 ~0 ~69 minecraft:cracked_stone_bricks
fill ~80 ~-15 ~75 ~81 ~0 ~79 minecraft:mossy_stone_bricks
# Eight monumental columns.
fill ~52 ~-17 ~62 ~54 ~2 ~64 minecraft:chiseled_stone_bricks
fill ~74 ~-17 ~62 ~76 ~2 ~64 minecraft:chiseled_stone_bricks
fill ~52 ~-17 ~80 ~54 ~2 ~82 minecraft:chiseled_stone_bricks
fill ~74 ~-17 ~80 ~76 ~2 ~82 minecraft:chiseled_stone_bricks
fill ~58 ~-17 ~57 ~60 ~2 ~59 minecraft:chiseled_stone_bricks
fill ~68 ~-17 ~57 ~70 ~2 ~59 minecraft:chiseled_stone_bricks
fill ~58 ~-17 ~85 ~60 ~2 ~87 minecraft:chiseled_stone_bricks
fill ~68 ~-17 ~85 ~70 ~2 ~87 minecraft:chiseled_stone_bricks
# Column capitals and broken arches.
fill ~51 ~2 ~61 ~55 ~3 ~65 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~73 ~2 ~61 ~77 ~3 ~65 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~51 ~2 ~79 ~55 ~3 ~83 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~73 ~2 ~79 ~77 ~3 ~83 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~57 ~2 ~56 ~61 ~3 ~60 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~67 ~2 ~56 ~71 ~3 ~60 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~57 ~2 ~84 ~61 ~3 ~88 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~67 ~2 ~84 ~71 ~3 ~88 minecraft:stone_brick_slab[type=top,waterlogged=false]
# High wall ribs / partial ruined ceiling crown.
fill ~55 ~5 ~56 ~73 ~5 ~58 minecraft:stone_brick_stairs[facing=south,half=top,shape=straight,waterlogged=false]
fill ~55 ~5 ~86 ~73 ~5 ~88 minecraft:stone_brick_stairs[facing=north,half=top,shape=straight,waterlogged=false]
fill ~48 ~5 ~63 ~50 ~5 ~81 minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]
fill ~78 ~5 ~63 ~80 ~5 ~81 minecraft:stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]
# Raised Eye pedestal at the preserved interaction coordinate ~64,-16,66.
fill ~62 ~-17 ~64 ~66 ~-17 ~68 minecraft:chiseled_deepslate
fill ~63 ~-16 ~65 ~65 ~-16 ~67 minecraft:polished_deepslate
setblock ~64 ~-15 ~66 minecraft:lodestone
setblock ~63 ~-16 ~64 minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]
setblock ~65 ~-16 ~64 minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]
# Wall alcoves / petrified victims.
fill ~62 ~-17 ~56 ~66 ~-12 ~57 minecraft:chiseled_stone_bricks
setblock ~63 ~-11 ~57 minecraft:skeleton_skull[rotation=8]
setblock ~65 ~-11 ~57 minecraft:wither_skeleton_skull[rotation=8]
fill ~62 ~-17 ~87 ~66 ~-12 ~88 minecraft:chiseled_stone_bricks
setblock ~63 ~-11 ~87 minecraft:player_head[rotation=0]
setblock ~65 ~-11 ~87 minecraft:skeleton_skull[rotation=0]
# Lighting points around the room.
setblock ~53 ~3 ~63 minecraft:soul_lantern[hanging=true]
setblock ~75 ~3 ~63 minecraft:soul_lantern[hanging=true]
setblock ~53 ~3 ~81 minecraft:soul_lantern[hanging=true]
setblock ~75 ~3 ~81 minecraft:soul_lantern[hanging=true]
setblock ~59 ~3 ~58 minecraft:soul_lantern[hanging=true]
setblock ~69 ~3 ~58 minecraft:soul_lantern[hanging=true]
setblock ~59 ~3 ~86 minecraft:soul_lantern[hanging=true]
setblock ~69 ~3 ~86 minecraft:soul_lantern[hanging=true]
# Ground debris and asymmetry.
setblock ~50 ~-17 ~72 minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]
setblock ~51 ~-17 ~73 minecraft:stone_brick_slab[type=bottom,waterlogged=false]
setblock ~78 ~-17 ~76 minecraft:cracked_stone_bricks
setblock ~77 ~-17 ~77 minecraft:moss_carpet
setblock ~57 ~-17 ~83 minecraft:cobweb
setblock ~72 ~-17 ~61 minecraft:cobweb
# Restore the destructible phase cover and the central prison at the original coordinates.
function medusa:dungeon/restore_cover
# Keep the west entrance rough opening; build_arena_approach carves the final reveal after this function.
fill ~47 ~-17 ~66 ~48 ~-12 ~70 minecraft:air
