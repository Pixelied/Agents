$execute if score @s fk_state matches 1 unless entity @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run function fallen_knight:arena/reset
$execute if score @s fk_state matches 0 unless entity @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run function fallen_knight:arena/spawn_dormant_boss
$execute if score @s fk_state matches 2 run kill @e[tag=fk.boss,scores={fk_aid=$(aid)}]
execute if score @s fk_state matches 2 run function fallen_knight:arena/unseal
$execute if score @s fk_state matches 2 run kill @e[tag=fk.spectral,scores={fk_aid=$(aid)}]
$execute if score @s fk_state matches 2 run kill @e[tag=fk.wave,scores={fk_aid=$(aid)}]
execute if score @s fk_state matches 3 run function fallen_knight:arena/reset
