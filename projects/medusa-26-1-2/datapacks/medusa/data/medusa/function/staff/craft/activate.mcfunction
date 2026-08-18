advancement revoke @s only medusa:events/medusa_staff_crafted
clear @s minecraft:breeze_rod[minecraft:custom_data~{md_item:"medusa_staff_candidate"}] 1
loot give @s loot medusa:items/medusa_staff
playsound minecraft:block.beacon.activate master @s ~ ~ ~ 0.8 1.35
