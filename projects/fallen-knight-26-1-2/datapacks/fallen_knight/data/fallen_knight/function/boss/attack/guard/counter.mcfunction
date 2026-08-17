# Shared hit-event hook (wired in Task 7) calls this only while Guard is active.
function fallen_knight:boss/director/face_target
playsound minecraft:item.shield.block hostile @a[distance=..20] ~ ~ ~ 1 0.55
execute store result storage fallen_knight:macro boss.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:boss/attack/guard/counter_for_arena with storage fallen_knight:macro boss
