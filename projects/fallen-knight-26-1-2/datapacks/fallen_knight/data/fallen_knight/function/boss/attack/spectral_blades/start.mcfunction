scoreboard players set @s fk_attack 11
scoreboard players set @s fk_prev 11
scoreboard players set @s fk_timer 0
scoreboard players set @s fk_cd_blades 120
data merge entity @s {NoAI:1b}
function fallen_knight:boss/attack/spectral_blades/spawn
