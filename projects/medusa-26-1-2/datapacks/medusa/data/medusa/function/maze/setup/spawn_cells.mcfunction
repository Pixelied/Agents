function medusa:maze/setup/spawn_cell {col:0}
execute positioned ~7 ~ ~ run function medusa:maze/setup/spawn_cell {col:1}
execute positioned ~14 ~ ~ run function medusa:maze/setup/spawn_cell {col:2}
execute positioned ~21 ~ ~ run function medusa:maze/setup/spawn_cell {col:3}
execute positioned ~28 ~ ~ run function medusa:maze/setup/spawn_cell {col:4}
execute positioned ~35 ~ ~ run function medusa:maze/setup/spawn_cell {col:5}
execute positioned ~42 ~ ~ run function medusa:maze/setup/spawn_cell {col:6}
execute positioned ~49 ~ ~ run function medusa:maze/setup/spawn_cell {col:7}
execute positioned ~56 ~ ~ run function medusa:maze/setup/spawn_cell {col:8}
execute positioned ~63 ~ ~ run function medusa:maze/setup/spawn_cell {col:9}
execute positioned ~70 ~ ~ run function medusa:maze/setup/spawn_cell {col:10}
execute positioned ~77 ~ ~ run function medusa:maze/setup/spawn_cell {col:11}
execute positioned ~84 ~ ~ run function medusa:maze/setup/spawn_cell {col:12}
scoreboard players add @s md_mrow 1
tp @s ~ ~ ~7
