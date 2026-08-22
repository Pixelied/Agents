summon minecraft:marker ~ ~ ~ {Tags:["md.maze.cell","md.maze.new_cell"]}
scoreboard players operation @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_eid = @s md_eid
scoreboard players operation @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_mrow = @s md_mrow
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_mcol $(col)
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_mn 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_me 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_ms 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_mw 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_nn 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_ne 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_ns 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_nw 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_mseen 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_mfront 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_mdist 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_mparent 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] md_mblocked 0
tag @e[type=minecraft:marker,tag=md.maze.new_cell,limit=1,sort=nearest] remove md.maze.new_cell
