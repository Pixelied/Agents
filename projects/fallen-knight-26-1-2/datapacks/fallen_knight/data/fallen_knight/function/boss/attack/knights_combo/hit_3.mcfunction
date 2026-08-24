data merge entity @s {NoAI:1b}
playsound minecraft:entity.player.attack.strong hostile @a[distance=..20] ~ ~ ~ 1 0.65
execute store result storage fallen_knight:macro boss.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:boss/attack/knights_combo/hit_3_for_arena with storage fallen_knight:macro boss
