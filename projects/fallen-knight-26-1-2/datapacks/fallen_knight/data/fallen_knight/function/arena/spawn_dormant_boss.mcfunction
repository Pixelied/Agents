execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:arena/kill_boss_for_arena with storage fallen_knight:macro arena
execute positioned ~ ~1 ~ summon minecraft:vindicator run function fallen_knight:boss/bootstrap_dormant
