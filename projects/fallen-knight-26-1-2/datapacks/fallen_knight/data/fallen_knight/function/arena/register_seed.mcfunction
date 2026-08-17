tag @s remove fk.arena_seed
tag @s add fk.arena
scoreboard players add $next fk_aid 1
scoreboard players operation @s fk_aid = $next fk_aid
scoreboard players set @s fk_state 0
scoreboard players set @s fk_clear 0
scoreboard players set @s fk_eid 0
scoreboard players set @s fk_join 0
scoreboard players set @s fk_reset 0
function fallen_knight:arena/spawn_dormant_boss
setblock ~ ~-2 ~ minecraft:air
