# Snake-infested inner sanctum: compressed ritual hall before the boss arena.
# connector: sanctum->arena_approach
# Main hall begins east of the Blind Passage gate at x=-2 so it cannot erase that gate.
fill ~-1 ~-18 ~58 ~43 ~-10 ~72 minecraft:stone_bricks
fill ~-1 ~-17 ~59 ~42 ~-11 ~71 minecraft:air
fill ~-1 ~-18 ~59 ~42 ~-18 ~71 minecraft:polished_deepslate
# Broken central procession path.
fill ~-1 ~-18 ~63 ~42 ~-18 ~67 minecraft:cracked_deepslate_tiles
fill ~5 ~-18 ~64 ~9 ~-18 ~66 minecraft:mossy_stone_bricks
fill ~17 ~-18 ~63 ~21 ~-18 ~67 minecraft:cracked_stone_bricks
fill ~30 ~-18 ~64 ~35 ~-18 ~66 minecraft:mossy_stone_bricks
# Repeating temple ribs create depth without a boxy corridor.
fill ~2 ~-17 ~59 ~3 ~-11 ~61 minecraft:chiseled_stone_bricks
fill ~2 ~-17 ~69 ~3 ~-11 ~71 minecraft:chiseled_stone_bricks
fill ~10 ~-17 ~59 ~11 ~-11 ~61 minecraft:cracked_stone_bricks
fill ~10 ~-17 ~69 ~11 ~-11 ~71 minecraft:mossy_stone_bricks
fill ~18 ~-17 ~59 ~19 ~-11 ~61 minecraft:chiseled_stone_bricks
fill ~18 ~-17 ~69 ~19 ~-11 ~71 minecraft:chiseled_stone_bricks
fill ~26 ~-17 ~59 ~27 ~-11 ~61 minecraft:mossy_stone_bricks
fill ~26 ~-17 ~69 ~27 ~-11 ~71 minecraft:cracked_stone_bricks
fill ~34 ~-17 ~59 ~35 ~-11 ~61 minecraft:chiseled_stone_bricks
fill ~34 ~-17 ~69 ~35 ~-11 ~71 minecraft:chiseled_stone_bricks
# Stone-brick arches between ribs.
fill ~2 ~-11 ~61 ~3 ~-11 ~69 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~10 ~-11 ~61 ~11 ~-11 ~69 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~18 ~-11 ~61 ~19 ~-11 ~69 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~26 ~-11 ~61 ~27 ~-11 ~69 minecraft:stone_brick_slab[type=top,waterlogged=false]
fill ~34 ~-11 ~61 ~35 ~-11 ~69 minecraft:stone_brick_slab[type=top,waterlogged=false]
# Serpent altars and petrified-victim plinths.
fill ~6 ~-17 ~60 ~8 ~-16 ~62 minecraft:chiseled_deepslate
setblock ~7 ~-15 ~61 minecraft:player_head[rotation=8]
fill ~14 ~-17 ~68 ~16 ~-16 ~70 minecraft:chiseled_deepslate
setblock ~15 ~-15 ~69 minecraft:skeleton_skull[rotation=4]
fill ~22 ~-17 ~60 ~24 ~-16 ~62 minecraft:chiseled_deepslate
setblock ~23 ~-15 ~61 minecraft:wither_skeleton_skull[rotation=12]
fill ~30 ~-17 ~68 ~32 ~-16 ~70 minecraft:chiseled_deepslate
setblock ~31 ~-15 ~69 minecraft:player_head[rotation=0]
# Corruption increases toward Medusa.
setblock ~5 ~-17 ~70 minecraft:cobweb
setblock ~12 ~-17 ~60 minecraft:cobweb
setblock ~20 ~-17 ~70 minecraft:cobweb
setblock ~28 ~-17 ~60 minecraft:cobweb
setblock ~36 ~-17 ~70 minecraft:cobweb
setblock ~8 ~-17 ~68 minecraft:moss_carpet
setblock ~16 ~-17 ~61 minecraft:moss_carpet
setblock ~24 ~-17 ~68 minecraft:moss_carpet
# Cold procession lighting.
setblock ~3 ~-10 ~65 minecraft:soul_lantern[hanging=true]
setblock ~11 ~-10 ~65 minecraft:soul_lantern[hanging=true]
setblock ~19 ~-10 ~65 minecraft:soul_lantern[hanging=true]
setblock ~27 ~-10 ~65 minecraft:soul_lantern[hanging=true]
setblock ~35 ~-10 ~65 minecraft:soul_lantern[hanging=true]
# Final narrowing throat toward the arena.
fill ~39 ~-17 ~61 ~43 ~-11 ~69 minecraft:stone_bricks
fill ~39 ~-17 ~62 ~43 ~-12 ~68 minecraft:air
fill ~39 ~-18 ~62 ~43 ~-18 ~68 minecraft:polished_deepslate
fill ~41 ~-17 ~62 ~42 ~-13 ~62 minecraft:cobblestone_wall
fill ~41 ~-17 ~68 ~42 ~-13 ~68 minecraft:cobblestone_wall
