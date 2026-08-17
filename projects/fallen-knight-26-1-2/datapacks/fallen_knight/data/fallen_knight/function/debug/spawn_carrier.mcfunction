summon minecraft:vindicator ~ ~ ~ {Tags:["fk.boss"],PersistenceRequired:1b,CanPickUpLoot:0b,Silent:1b}
execute as @e[type=minecraft:vindicator,tag=fk.boss,sort=nearest,limit=1,distance=..2] run function fallen_knight:boss/bootstrap
