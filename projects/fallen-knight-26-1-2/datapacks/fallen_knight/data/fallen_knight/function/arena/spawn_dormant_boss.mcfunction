execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
function fallen_knight:arena/kill_boss_for_arena with storage fallen_knight:macro arena
summon minecraft:vindicator ~ ~1 ~ {PersistenceRequired:1b,CanPickUpLoot:0b,Silent:1b,NoAI:1b,Invulnerable:1b}
tag @e[type=minecraft:vindicator,tag=!fk.boss,sort=nearest,limit=1,distance=..3] add fk.boss
execute as @e[type=minecraft:vindicator,tag=fk.boss,sort=nearest,limit=1,distance=..3] run function fallen_knight:boss/bootstrap
execute as @e[tag=fk.boss,sort=nearest,limit=1,distance=..3] run scoreboard players operation @s fk_aid = @e[tag=fk.arena,sort=nearest,limit=1,distance=..3] fk_aid
