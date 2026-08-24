$summon minecraft:marker ~$(x) ~-18 ~$(z) {Tags:["md.maze.trap","md.maze.new_trap"]}
scoreboard players operation @e[type=minecraft:marker,tag=md.maze.new_trap,limit=1,sort=nearest] md_eid = @s md_eid
$scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_trap,limit=1,sort=nearest] md_mtrap $(type)
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_trap,limit=1,sort=nearest] md_marmed 0
scoreboard players set @e[type=minecraft:marker,tag=md.maze.new_trap,limit=1,sort=nearest] md_mtrap_timer 0
tag @e[type=minecraft:marker,tag=md.maze.new_trap,limit=1,sort=nearest] remove md.maze.new_trap
