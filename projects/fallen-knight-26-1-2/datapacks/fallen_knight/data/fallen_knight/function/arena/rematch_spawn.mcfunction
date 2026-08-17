execute unless score @s fk_clear matches 1 run return 0
execute unless score @s fk_state matches 2 run return 0
execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:arena/cleanup_for_arena with storage fallen_knight:macro arena
function fallen_knight:arena/bossbar/remove with storage fallen_knight:macro arena
function fallen_knight:arena/spawn_dormant_boss
function fallen_knight:arena/start
