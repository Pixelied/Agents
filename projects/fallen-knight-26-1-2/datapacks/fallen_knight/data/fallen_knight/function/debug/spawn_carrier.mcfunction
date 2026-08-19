summon minecraft:vindicator ~ ~ ~ {PersistenceRequired:1b,CanPickUpLoot:0b,Silent:1b}
tag @e[type=minecraft:vindicator,tag=!fk.boss,sort=nearest,limit=1,distance=..2] add fk.boss
execute as @e[type=minecraft:vindicator,tag=fk.boss,sort=nearest,limit=1,distance=..2] run function fallen_knight:boss/bootstrap
