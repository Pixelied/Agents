# Snapshot the build phase so a stage can advance md_build without cascading into the next stage this tick.
scoreboard players operation @s md_tmp = @s md_build
execute if score @s md_tmp matches 1 run function medusa:instance/build/stage_1
execute if score @s md_tmp matches 2 run function medusa:instance/build/stage_2
execute if score @s md_tmp matches 3 run function medusa:instance/build/stage_3
execute if score @s md_tmp matches 4 run function medusa:instance/build/stage_4
execute if score @s md_tmp matches 5 run function medusa:instance/build/stage_5
execute if score @s md_tmp matches 6 run function medusa:instance/build/stage_6
execute if score @s md_tmp matches 7 run function medusa:instance/build/stage_7
execute if score @s md_tmp matches 8 run function medusa:instance/build/stage_8
execute if score @s md_tmp matches 9 run function medusa:instance/build/stage_9
execute if score @s md_tmp matches 10 run function medusa:instance/build/stage_10
execute if score @s md_tmp matches 11 run function medusa:instance/build/stage_11
execute if score @s md_tmp matches 12 run function medusa:instance/build/stage_12
