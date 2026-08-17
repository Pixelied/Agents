execute store result score $ritual_diamond fk_tmp run clear @s minecraft:diamond 0
execute store result score $ritual_soul fk_tmp run clear @s minecraft:soul_sand 0
execute store result score $ritual_iron fk_tmp run clear @s minecraft:iron_ingot 0
execute if score $ritual_diamond fk_tmp matches 1.. if score $ritual_soul fk_tmp matches 4.. if score $ritual_iron fk_tmp matches 4.. run function fallen_knight:ritual/activate
execute unless score $ritual_diamond fk_tmp matches 1.. run title @s actionbar {"text":"The ritual needs 1 Diamond, 4 Soul Sand, and 4 Iron Ingots.","color":"gray"}
execute if score $ritual_diamond fk_tmp matches 1.. unless score $ritual_soul fk_tmp matches 4.. run title @s actionbar {"text":"The ritual needs 1 Diamond, 4 Soul Sand, and 4 Iron Ingots.","color":"gray"}
execute if score $ritual_diamond fk_tmp matches 1.. if score $ritual_soul fk_tmp matches 4.. unless score $ritual_iron fk_tmp matches 4.. run title @s actionbar {"text":"The ritual needs 1 Diamond, 4 Soul Sand, and 4 Iron Ingots.","color":"gray"}
