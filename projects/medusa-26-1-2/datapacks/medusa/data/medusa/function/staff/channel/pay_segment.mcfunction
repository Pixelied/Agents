function medusa:staff/read_charges
scoreboard players set $pay md_tmp 0
execute if score @s md_staff matches 1.. run scoreboard players set $pay md_tmp 1
execute if score $pay md_tmp matches 1 run scoreboard players remove @s md_staff 1
execute if score $pay md_tmp matches 1 store result storage medusa:macro staff.charges int 1 run scoreboard players get @s md_staff
execute if score $pay md_tmp matches 1 run function medusa:staff/write_charges with storage medusa:macro staff
execute if score $pay md_tmp matches 0 run tag @s add md.staff_interrupted
