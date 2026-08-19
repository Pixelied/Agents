function fallen_knight:boss/bootstrap
data merge entity @s {NoAI:1b,Invulnerable:1b}
scoreboard players operation @s fk_aid = @e[type=minecraft:marker,tag=fk.arena,sort=nearest,limit=1,distance=..3] fk_aid
