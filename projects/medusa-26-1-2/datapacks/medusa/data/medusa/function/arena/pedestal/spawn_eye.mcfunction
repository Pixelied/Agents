execute store result storage medusa:macro eye.eid int 1 run scoreboard players get @s md_eid
function medusa:arena/pedestal/remove with storage medusa:macro eye
summon minecraft:item_display ~64 ~-15.8 ~66 {Tags:["md.pedestal_display","md.new_eye_display"],item:{id:"minecraft:player_head",count:1,components:{"minecraft:custom_name":'{"text":"Golden Gorgon Eye","color":"gold","italic":false}',"minecraft:custom_data":{md_item:"golden_gorgon_eye"},"minecraft:profile":{properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQ1NzdkOWU1YTVhZGM4ZTA5MzYyOTVlYjYzMDBmZGUwZmY5YjAyM2YyMGJlZmMxNTNiMjhkZWVlYTgwNDdhMSJ9fX0="}]}}},billboard:"fixed",transformation:{scale:[0.8f,0.8f,0.8f]}}
execute store result score @e[type=minecraft:item_display,tag=md.new_eye_display,distance=..110,limit=1,sort=nearest] md_eid run scoreboard players get @s md_eid
tag @e[type=minecraft:item_display,tag=md.new_eye_display,distance=..110] remove md.new_eye_display
summon minecraft:interaction ~64 ~-16 ~66 {Tags:["md.pedestal_interaction","md.new_eye_interaction"],width:1.2f,height:1.5f,response:1b}
execute store result score @e[type=minecraft:interaction,tag=md.new_eye_interaction,distance=..110,limit=1,sort=nearest] md_eid run scoreboard players get @s md_eid
tag @e[type=minecraft:interaction,tag=md.new_eye_interaction,distance=..110] remove md.new_eye_interaction
scoreboard players set @s md_eye_state 0
