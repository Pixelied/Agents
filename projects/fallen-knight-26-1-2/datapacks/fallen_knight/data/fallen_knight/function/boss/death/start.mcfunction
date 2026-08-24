scoreboard players set @s fk_phase 4
scoreboard players set @s fk_attack 0
scoreboard players set @s fk_timer 0
data merge entity @s {NoAI:1b,Invulnerable:1b}
execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:arena/cleanup_for_arena with storage fallen_knight:macro arena
