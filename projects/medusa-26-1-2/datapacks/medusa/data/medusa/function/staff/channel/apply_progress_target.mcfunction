tag @s add md.staff_beamed
$scoreboard players set @s md_staff_beam_tick $(tick)
$execute if score @s md_staff_beam_tick matches 1..19 run effect give @s minecraft:slowness 2 4 true
$execute if score @s md_staff_beam_tick matches 1..19 run effect give @s minecraft:weakness 2 1 true
$execute if score @s md_staff_beam_tick matches 20..39 run effect give @s minecraft:slowness 2 7 true
$execute if score @s md_staff_beam_tick matches 20..39 run effect give @s minecraft:weakness 2 2 true
$execute if score @s md_staff_beam_tick matches 40..59 run effect give @s minecraft:slowness 2 12 true
$execute if score @s md_staff_beam_tick matches 40..59 run effect give @s minecraft:weakness 2 4 true
$execute if score @s md_staff_beam_tick matches 60.. unless entity @s[tag=md.staff_petrified] run function medusa:staff/channel/full_petrify
execute if entity @s[tag=md.staff_petrified] run scoreboard players set @s md_staff_stone_timer 0
scoreboard players operation @s md_tmp = @s md_staff_beam_tick
scoreboard players operation @s md_tmp %= $20 md_tmp
execute if score @s md_staff_beam_tick matches 100.. if score @s md_tmp matches 0 unless entity @s[tag=md.boss] run damage @s 2 medusa:petrification
particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~1 ~ 0.25 0.5 0.25 0.02 6 force
