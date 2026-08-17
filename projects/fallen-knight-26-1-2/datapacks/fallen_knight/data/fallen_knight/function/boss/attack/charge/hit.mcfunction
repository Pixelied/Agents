execute store result storage fallen_knight:macro boss.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:boss/attack/charge/hit_for_arena with storage fallen_knight:macro boss
execute if entity @a[tag=fk.charge_hit] run scoreboard players set @s fk_timer 25
tag @a remove fk.charge_hit
