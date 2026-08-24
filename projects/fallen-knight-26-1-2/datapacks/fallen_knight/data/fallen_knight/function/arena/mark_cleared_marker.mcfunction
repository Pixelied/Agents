scoreboard players set @s fk_clear 1
scoreboard players set @s fk_state 2
scoreboard players set @s fk_join 0
scoreboard players set @s fk_reset 0
function fallen_knight:arena/unseal
execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:arena/bossbar/remove with storage fallen_knight:macro arena
function fallen_knight:arena/cleanup_for_arena with storage fallen_knight:macro arena
