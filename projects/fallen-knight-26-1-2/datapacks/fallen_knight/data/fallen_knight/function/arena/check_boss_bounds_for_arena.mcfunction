$execute if entity @e[tag=fk.boss,scores={fk_aid=$(aid)},distance=..15,limit=1] run scoreboard players set @s fk_tmp 0
$execute unless entity @e[tag=fk.boss,scores={fk_aid=$(aid)},distance=..15,limit=1] if entity @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run scoreboard players add @s fk_tmp 1
$execute if score @s fk_tmp matches 1..2 run tp @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] ~ ~1 ~
$execute if score @s fk_tmp matches 1..2 as @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run scoreboard players set @s fk_attack 0
$execute if score @s fk_tmp matches 1..2 as @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run data merge entity @s {NoAI:0b}
execute if score @s fk_tmp matches 3.. run function fallen_knight:arena/reset
