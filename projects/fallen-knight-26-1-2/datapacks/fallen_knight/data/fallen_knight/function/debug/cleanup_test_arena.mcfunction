execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:arena/unseal
function fallen_knight:arena/kill_boss_for_arena with storage fallen_knight:macro arena
function fallen_knight:arena/bossbar/remove with storage fallen_knight:macro arena
function fallen_knight:arena/cleanup_for_arena with storage fallen_knight:macro arena
function fallen_knight:arena/participants/clear_for_arena with storage fallen_knight:macro arena
kill @s
