summon minecraft:vindicator ~ ~ ~
tag @e[type=minecraft:vindicator,tag=!fk.boss,sort=nearest,limit=1,distance=..2] add fk.boss
execute as @e[type=minecraft:vindicator,tag=fk.boss,sort=nearest,limit=1,distance=..2] run function fallen_knight:boss/bootstrap
