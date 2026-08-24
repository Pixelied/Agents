tag @s add md.staff_petrified
scoreboard players set @s md_staff_stone_timer 0
scoreboard players set @s md_staff_stone_limit 100
execute if entity @s[tag=md.boss] run scoreboard players set @s md_staff_stone_limit 30
tag @s remove md.staff_noai_applied
execute unless entity @s[type=minecraft:player] unless data entity @s {NoAI:1b} run tag @s add md.staff_noai_applied
execute if entity @s[tag=md.staff_noai_applied] run data merge entity @s {NoAI:1b}
execute store result storage medusa:macro staff_stone.tid int 1 run scoreboard players get @s md_tid
function medusa:staff/channel/clear_helpers with storage medusa:macro staff_stone
summon minecraft:marker ~ ~ ~ {Tags:["md.staff_stone_anchor","md.new_staff_stone_anchor"]}
scoreboard players operation @e[type=minecraft:marker,tag=md.new_staff_stone_anchor,distance=..2,limit=1,sort=nearest] md_tid = @s md_tid
tag @e[type=minecraft:marker,tag=md.new_staff_stone_anchor,distance=..2] remove md.new_staff_stone_anchor
summon minecraft:block_display ~ ~ ~ {Tags:["md.staff_stone_shell","md.new_staff_stone_shell"],block_state:{Name:"minecraft:stone"},transformation:{translation:[-0.55f,0.0f,-0.55f],left_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[1.1f,1.9f,1.1f],right_rotation:[0.0f,0.0f,0.0f,1.0f]}}
scoreboard players operation @e[type=minecraft:block_display,tag=md.new_staff_stone_shell,distance=..2,limit=1,sort=nearest] md_tid = @s md_tid
tag @e[type=minecraft:block_display,tag=md.new_staff_stone_shell,distance=..2] remove md.new_staff_stone_shell
playsound minecraft:block.stone.place master @a[distance=..16] ~ ~ ~ 0.9 0.55
