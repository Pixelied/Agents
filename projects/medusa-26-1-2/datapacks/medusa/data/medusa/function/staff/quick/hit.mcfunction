tag @e[tag=!md.staff_caster,type=!minecraft:marker,type=!minecraft:item,type=!minecraft:item_display,type=!minecraft:block_display,type=!minecraft:text_display,type=!minecraft:interaction,type=!minecraft:armor_stand,distance=..0.8,limit=1,sort=nearest] add md.staff_target_temp
effect give @e[tag=md.staff_target_temp,limit=1] minecraft:slowness 4 4 true
effect give @e[tag=md.staff_target_temp,limit=1] minecraft:weakness 4 2 true
scoreboard players add @e[tag=md.staff_target_temp,limit=1] md_petr 250
particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~ ~ 0.35 0.5 0.35 0.03 18 force
tag @e[tag=md.staff_target_temp] remove md.staff_target_temp
