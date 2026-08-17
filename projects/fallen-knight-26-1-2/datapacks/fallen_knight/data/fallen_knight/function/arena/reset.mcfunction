scoreboard players set @s fk_state 3
function fallen_knight:arena/cleanup
execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:arena/kill_boss_for_arena with storage fallen_knight:macro arena
function fallen_knight:arena/participants/clear_for_arena with storage fallen_knight:macro arena
scoreboard players set @s fk_join 0
scoreboard players set @s fk_reset 0
execute if score @s fk_clear matches 0 run function fallen_knight:arena/reset_uncleared
execute if score @s fk_clear matches 1.. run scoreboard players set @s fk_state 2
