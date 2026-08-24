$summon minecraft:block_display ~$(x) ~1 ~$(z) {Tags:["md.maze.wall_display","md.maze.new_wall_display"],block_state:{Name:"minecraft:stone_bricks"},billboard:"fixed",view_range:1.25f,interpolation_duration:60,start_interpolation:0,transformation:{translation:[0.0f,$(ystart)f,0.0f],left_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[$(sx)f,7.0f,$(sz)f],right_rotation:[0.0f,0.0f,0.0f,1.0f]}}
scoreboard players operation @e[type=minecraft:block_display,tag=md.maze.new_wall_display,distance=..6,limit=1,sort=nearest] md_eid = @s md_eid
scoreboard players operation @e[type=minecraft:block_display,tag=md.maze.new_wall_display,distance=..6,limit=1,sort=nearest] md_mrow = @s md_mrow
scoreboard players operation @e[type=minecraft:block_display,tag=md.maze.new_wall_display,distance=..6,limit=1,sort=nearest] md_mcol = @s md_mcol
scoreboard players operation @e[type=minecraft:block_display,tag=md.maze.new_wall_display,distance=..6,limit=1,sort=nearest] md_morient = @s md_roll
tag @e[type=minecraft:block_display,tag=md.maze.new_wall_display,distance=..6,limit=1,sort=nearest] remove md.maze.new_wall_display
