execute store result storage medusa:macro staff_stone.tid int 1 run scoreboard players get @s md_tid
function medusa:staff/target/freeze_to_anchor with storage medusa:macro staff_stone
effect give @s minecraft:invisibility 1 0 true
effect give @s minecraft:slowness 1 255 true
effect give @s minecraft:weakness 1 255 true
execute if entity @s[type=minecraft:player] run effect give @s minecraft:mining_fatigue 1 255 true
execute if entity @s[tag=md.staff_beamed] run scoreboard players set @s md_staff_stone_timer 0
execute unless entity @s[tag=md.staff_beamed] run scoreboard players add @s md_staff_stone_timer 1
tag @s remove md.staff_beamed
execute if score @s md_staff_stone_timer >= @s md_staff_stone_limit run function medusa:staff/channel/release_target
