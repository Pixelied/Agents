playsound minecraft:entity.player.attack.strong hostile @a[distance=..24] ~ ~ ~ 1 0.55
execute store result storage fallen_knight:macro boss.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:boss/attack/heavy_combo/hit_3_for_arena with storage fallen_knight:macro boss
