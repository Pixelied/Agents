execute store result storage medusa:macro recover.eid int 1 run scoreboard players get @s md_eid
execute if score @s md_state matches 0 if score @s md_eye_state matches 0 run function medusa:arena/pedestal/ensure_eye with storage medusa:macro recover
execute if score @s md_state matches 4 if score @s md_eye_state matches 0 run function medusa:arena/pedestal/ensure_eye with storage medusa:macro recover
execute if score @s md_state matches 0 if score @s md_eye_state matches 1 run function medusa:reward/return_eye
execute if score @s md_state matches 4 if score @s md_eye_state matches 1 run function medusa:reward/return_eye
