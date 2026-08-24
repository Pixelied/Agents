execute if items entity @s weapon.mainhand minecraft:diamond_sword[minecraft:custom_data~{fk_item:"oathbreaker"}] if entity @s[scores={fk_swing=16..,fk_cleave=0}] run function fallen_knight:item/cleave
scoreboard players set @s fk_swing 0
execute store result storage fallen_knight:macro hit.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:item/guard_counter_for_arena with storage fallen_knight:macro hit
advancement revoke @s only fallen_knight:events/player_hurt_entity
