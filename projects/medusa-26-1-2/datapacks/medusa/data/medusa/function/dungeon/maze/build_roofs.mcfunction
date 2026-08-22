# Interlocking barrel-vault roof system. Every traversable maze cell is enclosed.
# Long ribs plus dark mechanical slots make the shifting architecture legible from below.
# Thirteen north/south vault strips.
execute positioned ~-44 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~-37 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~-30 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~-23 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~-16 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~-9 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~-2 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~5 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~12 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~19 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~26 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~33 ~ ~ run function medusa:dungeon/maze/roof_column
execute positioned ~40 ~ ~ run function medusa:dungeon/maze/roof_column
# Thirteen heavy transverse ribs break up the ceiling and act as landmarks.
execute positioned ~ ~ ~30 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~37 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~44 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~51 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~58 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~65 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~72 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~79 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~86 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~93 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~100 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~107 run function medusa:dungeon/maze/roof_row
execute positioned ~ ~ ~114 run function medusa:dungeon/maze/roof_row
# Continuous mechanical cavities above every internal wall band.
execute positioned ~-41 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~-34 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~-27 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~-20 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~-13 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~-6 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~1 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~8 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~15 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~22 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~29 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~36 ~ ~ run function medusa:dungeon/maze/cavity_x
execute positioned ~ ~ ~33 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~40 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~47 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~54 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~61 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~68 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~75 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~82 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~89 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~96 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~103 run function medusa:dungeon/maze/cavity_z
execute positioned ~ ~ ~110 run function medusa:dungeon/maze/cavity_z
# Sparse hanging lights make recognizable waypoints without turning the maze into an arrow trail.
setblock ~-30 ~-5 ~30 minecraft:chain[axis=y,waterlogged=false]
setblock ~-30 ~-6 ~30 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~12 ~-5 ~37 minecraft:chain[axis=y,waterlogged=false]
setblock ~12 ~-6 ~37 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~-9 ~-5 ~44 minecraft:chain[axis=y,waterlogged=false]
setblock ~-9 ~-6 ~44 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~33 ~-5 ~51 minecraft:chain[axis=y,waterlogged=false]
setblock ~33 ~-6 ~51 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~-37 ~-5 ~58 minecraft:chain[axis=y,waterlogged=false]
setblock ~-37 ~-6 ~58 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~5 ~-5 ~65 minecraft:chain[axis=y,waterlogged=false]
setblock ~5 ~-6 ~65 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~-2 ~-5 ~72 minecraft:chain[axis=y,waterlogged=false]
setblock ~-2 ~-6 ~72 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~-23 ~-5 ~79 minecraft:chain[axis=y,waterlogged=false]
setblock ~-23 ~-6 ~79 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~26 ~-5 ~86 minecraft:chain[axis=y,waterlogged=false]
setblock ~26 ~-6 ~86 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~-9 ~-5 ~93 minecraft:chain[axis=y,waterlogged=false]
setblock ~-9 ~-6 ~93 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~-37 ~-5 ~100 minecraft:chain[axis=y,waterlogged=false]
setblock ~-37 ~-6 ~100 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~33 ~-5 ~100 minecraft:chain[axis=y,waterlogged=false]
setblock ~33 ~-6 ~100 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~12 ~-5 ~107 minecraft:chain[axis=y,waterlogged=false]
setblock ~12 ~-6 ~107 minecraft:soul_lantern[hanging=true,waterlogged=false]
setblock ~-16 ~-5 ~114 minecraft:chain[axis=y,waterlogged=false]
setblock ~-16 ~-6 ~114 minecraft:soul_lantern[hanging=true,waterlogged=false]
