execute store result score @s md_roll run random value 1..100
execute if score @s md_phase matches 1 if score @s md_roll matches 1..40 run function medusa:boss/attack/claw/start
execute if score @s md_phase matches 1 if score @s md_roll matches 41..70 run function medusa:boss/attack/serpent_lash/start
execute if score @s md_phase matches 1 if score @s md_roll matches 71..100 run function medusa:boss/attack/gorgon_gaze/start
execute if score @s md_phase matches 2 if score @s md_roll matches 1..25 run function medusa:boss/attack/claw/start
execute if score @s md_phase matches 2 if score @s md_roll matches 26..45 run function medusa:boss/attack/serpent_lash/start
execute if score @s md_phase matches 2 if score @s md_roll matches 46..65 run function medusa:boss/attack/venom_spit/start
execute if score @s md_phase matches 2 if score @s md_roll matches 66..80 run function medusa:boss/attack/brood_call/start
execute if score @s md_phase matches 2 if score @s md_roll matches 81..100 run function medusa:boss/attack/gorgon_gaze/start
execute if score @s md_phase matches 3 if score @s md_roll matches 1..20 run function medusa:boss/attack/claw/start
execute if score @s md_phase matches 3 if score @s md_roll matches 21..35 run function medusa:boss/attack/serpent_lash/start
execute if score @s md_phase matches 3 if score @s md_roll matches 36..52 run function medusa:boss/attack/venom_spit/start
execute if score @s md_phase matches 3 if score @s md_roll matches 53..65 run function medusa:boss/attack/brood_call/start
execute if score @s md_phase matches 3 if score @s md_roll matches 66..85 run function medusa:boss/attack/gorgon_gaze/start
execute if score @s md_phase matches 3 if score @s md_roll matches 86..100 run function medusa:boss/attack/large_serpent/start
