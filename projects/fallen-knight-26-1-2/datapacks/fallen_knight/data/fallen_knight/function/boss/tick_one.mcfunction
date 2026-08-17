execute store result score @s fk_hp run data get entity @s Health 1
execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
execute store result storage fallen_knight:macro arena.hp int 1 run scoreboard players get @s fk_hp
execute if score @s fk_aid matches 1.. run function fallen_knight:arena/bossbar/update with storage fallen_knight:macro arena
function fallen_knight:boss/cooldowns
execute if score @s fk_phase matches 1 if score @s fk_attack matches 0 run function fallen_knight:boss/director/select_phase1
execute if score @s fk_phase matches 1 if score @s fk_attack matches 1 run function fallen_knight:boss/attack/guard/tick
execute if score @s fk_phase matches 1 if score @s fk_attack matches 2 run function fallen_knight:boss/attack/shield_bash/tick
execute if score @s fk_phase matches 1 if score @s fk_attack matches 3 run function fallen_knight:boss/attack/knights_combo/tick
execute if score @s fk_phase matches 1 if score @s fk_attack matches 4 run function fallen_knight:boss/attack/overhead/tick
execute if score @s fk_phase matches 1 if score @s fk_attack matches 5 run function fallen_knight:boss/attack/charge/tick
