execute store result score $scales md_tmp run clear @s minecraft:turtle_scute[minecraft:custom_data~{md_item:"gorgon_scale"}] 0
execute store result score $fangs md_tmp run clear @s minecraft:flint[minecraft:custom_data~{md_item:"serpent_fang"}] 0
execute if items entity @s weapon.mainhand minecraft:player_head[minecraft:custom_data~{md_item:"golden_gorgon_eye"}] if score $scales md_tmp matches 4.. if score $fangs md_tmp matches 1.. run function medusa:ritual/consume_and_commit with storage medusa:macro ritual
execute unless items entity @s weapon.mainhand minecraft:player_head[minecraft:custom_data~{md_item:"golden_gorgon_eye"}] run tellraw @s {"text":"Hold the Golden Gorgon Eye to begin the ritual.","color":"gray"}
execute if score $scales md_tmp matches ..3 run tellraw @s {"text":"The ritual needs 4 Gorgon Scales.","color":"gray"}
execute if score $fangs md_tmp matches 0 run tellraw @s {"text":"The ritual needs 1 Serpent Fang.","color":"gray"}
