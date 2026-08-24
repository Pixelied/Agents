# Progress bands: 1-19 strong slow, 20-39 heavier, 40-59 near-immobile, 60+ full stone, 100+ crushing.
scoreboard players set @s md_tmp 0
execute if entity @s[tag=md.staff_interrupted] run scoreboard players set @s md_tmp 1
execute unless items entity @s weapon.mainhand minecraft:breeze_rod[minecraft:custom_data~{md_item:"medusa_staff"}] run scoreboard players set @s md_tmp 1
execute if score @s md_tmp matches 1 run function medusa:staff/channel/interrupt
execute if score @s md_tmp matches 0 run function medusa:staff/target/raycast_start
execute if score @s md_tmp matches 0 unless score @s md_staff_hit matches 1 run scoreboard players set @s md_tmp 1
execute if score @s md_tmp matches 1 run function medusa:staff/channel/interrupt
execute if score @s md_tmp matches 0 run effect give @s minecraft:slowness 1 4 true
scoreboard players operation $segment md_tmp = @s md_use
scoreboard players operation $segment md_tmp %= $20 md_tmp
execute if score @s md_tmp matches 0 if score @s md_use matches 21.. if score $segment md_tmp matches 1 run function medusa:staff/channel/pay_segment
execute if entity @s[tag=md.staff_interrupted] run scoreboard players set @s md_tmp 1
execute if score @s md_tmp matches 1 run function medusa:staff/channel/interrupt
execute if score @s md_tmp matches 0 store result storage medusa:macro channel.lock int 1 run scoreboard players get @s md_lock
execute if score @s md_tmp matches 0 store result storage medusa:macro channel.tick int 1 run scoreboard players get @s md_use
execute if score @s md_tmp matches 0 run function medusa:staff/channel/apply_progress with storage medusa:macro channel
execute if score @s md_tmp matches 0 run scoreboard players add @s md_use 1
