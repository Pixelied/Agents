function medusa:staff/read_charges
execute if score @s md_staff matches ..63 run clear @s minecraft:scute[minecraft:custom_data~{md_item:"gorgon_scale"}] 1
execute if score @s md_staff matches ..63 run scoreboard players add @s md_staff 8
execute if score @s md_staff matches 65.. run scoreboard players set @s md_staff 64
execute if score @s md_staff matches 1..64 store result storage medusa:macro staff.charges int 1 run scoreboard players get @s md_staff
execute if score @s md_staff matches 1..64 run function medusa:staff/write_charges with storage medusa:macro staff
execute if score @s md_staff matches 1..64 run playsound minecraft:block.amethyst_block.resonate master @s ~ ~ ~ 0.7 1.4
execute if score @s md_staff matches 64 run title @s actionbar {"text":"Gorgon Charges: 64 / 64","color":"green"}
