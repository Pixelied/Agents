summon minecraft:endermite ~1 ~ ~ {Tags:["md.snake","md.maze.trap_helper","md.maze.new_trap_helper"],PersistenceRequired:1b}
summon minecraft:endermite ~-1 ~ ~ {Tags:["md.snake","md.maze.trap_helper","md.maze.new_trap_helper"],PersistenceRequired:1b}
execute if score @s md_roll matches 0 store result score @s md_roll run random value 0..2
execute if score @s md_roll matches 1.. run summon minecraft:endermite ~ ~ ~1 {Tags:["md.snake","md.maze.trap_helper","md.maze.new_trap_helper"],PersistenceRequired:1b}
execute if score @s md_roll matches 2 run summon minecraft:endermite ~ ~ ~-1 {Tags:["md.snake","md.maze.trap_helper","md.maze.new_trap_helper"],PersistenceRequired:1b}
scoreboard players operation @e[tag=md.maze.new_trap_helper,distance=..6] md_eid = @s md_eid
tag @e[tag=md.maze.new_trap_helper,distance=..6] remove md.maze.new_trap_helper
