scoreboard players set $count md_tmp 0
$execute as @a[tag=md.participant,scores={md_eid=$(eid)}] run scoreboard players add $count md_tmp 1
scoreboard players operation @s md_count = $count md_tmp
execute if score @s md_count matches ..0 run scoreboard players set @s md_count 1
scoreboard players set @s md_maxhp 300
scoreboard players operation @s md_tmp = @s md_count
scoreboard players remove @s md_tmp 1
scoreboard players operation @s md_tmp *= $75 md_tmp
scoreboard players operation @s md_maxhp += @s md_tmp
execute if score @s md_maxhp matches 601.. run scoreboard players set @s md_maxhp 600
scoreboard players operation @s md_p2hp = @s md_maxhp
scoreboard players operation @s md_p2hp *= $60 md_tmp
scoreboard players operation @s md_p2hp /= $100 md_tmp
scoreboard players operation @s md_p3hp = @s md_maxhp
scoreboard players operation @s md_p3hp *= $28 md_tmp
scoreboard players operation @s md_p3hp /= $100 md_tmp
execute store result storage medusa:macro boss.maxhp int 1 run scoreboard players get @s md_maxhp
execute store result storage medusa:macro boss.p2hp int 1 run scoreboard players get @s md_p2hp
execute store result storage medusa:macro boss.p3hp int 1 run scoreboard players get @s md_p3hp
function medusa:boss/health/apply_scale_value with storage medusa:macro boss
