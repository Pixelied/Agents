tag @s add fk.server_smoke
function fallen_knight:boss/bootstrap
data merge entity @s {NoAI:1b,Invulnerable:1b,NoGravity:1b}
scoreboard players set @s fk_aid 999999
scoreboard players set @s fk_maxhp 160
scoreboard players set @s fk_halfhp 80
scoreboard players set @s fk_joinhp 40
