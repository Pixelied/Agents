scoreboard players add @s md_mtry 1
scoreboard players set @s md_mdelta 0
scoreboard players set @s md_mtick 0
scoreboard players set $two md_tmp 2
execute store result score @s md_count run random value 8..14
scoreboard players operation @s md_count *= $two md_tmp
execute store result storage medusa:macro maze.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/propose/copy_current with storage medusa:macro maze
scoreboard players set @s md_mphase 3
