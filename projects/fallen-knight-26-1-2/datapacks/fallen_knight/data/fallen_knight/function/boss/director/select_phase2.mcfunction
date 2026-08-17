function fallen_knight:boss/director/face_target
execute store result score @s fk_roll run random value 1..100
execute store result storage fallen_knight:macro boss.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:boss/director/select_phase2_for_arena with storage fallen_knight:macro boss
