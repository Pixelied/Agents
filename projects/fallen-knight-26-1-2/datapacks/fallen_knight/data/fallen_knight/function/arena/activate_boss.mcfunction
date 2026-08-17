$execute as @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run data merge entity @s {NoAI:0b,Invulnerable:0b,Silent:0b}
$execute as @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run scoreboard players set @s fk_phase 1
