# Console-safe smoke test used by CI. Uses an isolated high arena ID and cleans itself up.
kill @e[tag=fk.server_smoke]
summon minecraft:marker ~ ~ ~ {Tags:["fk.arena","fk.server_smoke"]}
scoreboard players set @e[type=minecraft:marker,tag=fk.server_smoke,sort=nearest,limit=1] fk_aid 999999
scoreboard players set @e[type=minecraft:marker,tag=fk.server_smoke,sort=nearest,limit=1] fk_state 1
scoreboard players set @e[type=minecraft:marker,tag=fk.server_smoke,sort=nearest,limit=1] fk_maxhp 160
summon minecraft:vindicator ~ ~1 ~ {Tags:["fk.boss","fk.server_smoke"],PersistenceRequired:1b,CanPickUpLoot:0b,Silent:1b,NoAI:1b,Invulnerable:1b}
execute as @e[type=minecraft:vindicator,tag=fk.server_smoke,sort=nearest,limit=1] run function fallen_knight:boss/bootstrap
scoreboard players set @e[type=minecraft:vindicator,tag=fk.server_smoke,sort=nearest,limit=1] fk_aid 999999
scoreboard players set @e[type=minecraft:vindicator,tag=fk.server_smoke,sort=nearest,limit=1] fk_maxhp 160
scoreboard players set @e[type=minecraft:vindicator,tag=fk.server_smoke,sort=nearest,limit=1] fk_halfhp 80
scoreboard players set @e[type=minecraft:vindicator,tag=fk.server_smoke,sort=nearest,limit=1] fk_joinhp 40
function fallen_knight:arena/activate_boss {aid:999999}
function fallen_knight:arena/bossbar/create {aid:999999,maxhp:160}
execute unless entity @e[type=minecraft:vindicator,tag=fk.server_smoke,tag=fk.boss,scores={fk_phase=1}] run say FK_SMOKE_FAIL_BOSS
execute store result score $smoke_bar fk_tmp run bossbar get fallen_knight:arena_999999 value
execute unless score $smoke_bar fk_tmp matches 1.. run say FK_SMOKE_FAIL_BOSSBAR
execute as @e[type=minecraft:vindicator,tag=fk.server_smoke,sort=nearest,limit=1] at @s run function fallen_knight:boss/tick_one
bossbar remove fallen_knight:arena_999999
kill @e[tag=fk.server_smoke]
