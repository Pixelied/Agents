execute store result storage medusa:macro staff_stone.tid int 1 run scoreboard players get @s md_tid
function medusa:staff/channel/clear_helpers with storage medusa:macro staff_stone
execute if entity @s[tag=md.staff_noai_applied] run data merge entity @s {NoAI:0b}
tag @s remove md.staff_noai_applied
tag @s remove md.staff_petrified
tag @s remove md.staff_beamed
scoreboard players set @s md_staff_stone_timer 0
scoreboard players set @s md_staff_stone_limit 0
scoreboard players set @s md_staff_beam_tick 0
particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~1 ~ 0.5 0.8 0.5 0.08 35 force
playsound minecraft:block.stone.break master @a[distance=..16] ~ ~ ~ 0.9 1.15
