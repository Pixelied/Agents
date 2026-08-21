# Stage 2: called on a later tick, once newly summoned entities are selector-visible.
execute unless entity @e[type=minecraft:marker,tag=fk.server_smoke,tag=fk.arena,scores={fk_aid=999999}] run say FK_SMOKE_FAIL_ARENA
execute unless entity @e[type=minecraft:vindicator,tag=fk.server_smoke,tag=fk.boss,scores={fk_aid=999999}] run say FK_SMOKE_FAIL_BOSS_SUMMON
function fallen_knight:arena/activate_boss {aid:999999}
function fallen_knight:arena/bossbar/create {aid:999999,maxhp:160}
execute unless entity @e[type=minecraft:vindicator,tag=fk.server_smoke,tag=fk.boss,scores={fk_phase=1}] run say FK_SMOKE_FAIL_BOSS
execute store result score $smoke_bar fk_tmp run bossbar get fallen_knight:arena_999999 value
execute unless score $smoke_bar fk_tmp matches 1.. run say FK_SMOKE_FAIL_BOSSBAR
execute as @e[type=minecraft:vindicator,tag=fk.server_smoke,sort=nearest,limit=1] at @s run function fallen_knight:boss/tick_one
bossbar remove fallen_knight:arena_999999
kill @e[tag=fk.server_smoke]
