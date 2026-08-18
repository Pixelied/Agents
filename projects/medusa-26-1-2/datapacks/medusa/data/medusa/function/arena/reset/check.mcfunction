scoreboard players set $alive md_tmp 0
$execute as @a[tag=md.participant,scores={md_eid=$(eid)}] run scoreboard players add $alive md_tmp 1
scoreboard players operation @s md_count = $alive md_tmp
execute if score @s md_count matches 0 run scoreboard players add @s md_reset 1
execute if score @s md_count matches 1.. run scoreboard players set @s md_reset 0
execute if score @s md_reset matches 100.. run function medusa:arena/reset/start
