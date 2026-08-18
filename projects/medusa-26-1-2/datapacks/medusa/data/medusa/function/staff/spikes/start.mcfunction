function medusa:staff/read_charges
scoreboard players set @s md_staff_hit 0
scoreboard players set @s md_ray 0
scoreboard players set $spikes_paid md_tmp 0
tag @s add md.staff_spike_caster
execute anchored eyes positioned ^ ^ ^0.5 run function medusa:staff/spikes/ground_ray
execute if score @s md_staff_hit matches 1 if score @s md_staff matches 4.. run scoreboard players set $spikes_paid md_tmp 1
execute if score $spikes_paid md_tmp matches 1 run scoreboard players remove @s md_staff 4
execute if score $spikes_paid md_tmp matches 1 store result storage medusa:macro staff.charges int 1 run scoreboard players get @s md_staff
execute if score $spikes_paid md_tmp matches 1 run function medusa:staff/write_charges with storage medusa:macro staff
execute if score $spikes_paid md_tmp matches 1 as @e[type=minecraft:marker,tag=md.new_staff_spike_origin,limit=1,sort=nearest] at @s run function medusa:staff/spikes/spawn_cluster
execute if score @s md_staff_hit matches 1 if score $spikes_paid md_tmp matches 0 run title @s actionbar {"text":"Stone Spikes need 4 Gorgon Charges.","color":"dark_gray"}
kill @e[type=minecraft:marker,tag=md.new_staff_spike_origin]
tag @s remove md.staff_spike_caster
tag @s remove md.staff_spikes_pending
scoreboard players set @s md_use 1
