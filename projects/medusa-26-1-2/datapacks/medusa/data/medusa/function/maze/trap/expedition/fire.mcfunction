summon minecraft:husk ~2 ~ ~ {Tags:["md.maze.trap_helper","md.maze.new_trap_helper"],PersistenceRequired:1b}
summon minecraft:husk ~-2 ~ ~ {Tags:["md.maze.trap_helper","md.maze.new_trap_helper"],PersistenceRequired:1b}
scoreboard players operation @e[type=minecraft:husk,tag=md.maze.new_trap_helper,distance=..6] md_eid = @s md_eid
tag @e[type=minecraft:husk,tag=md.maze.new_trap_helper,distance=..6] remove md.maze.new_trap_helper
playsound minecraft:entity.husk.ambient master @a[distance=..18] ~ ~1 ~ 0.9 0.7
