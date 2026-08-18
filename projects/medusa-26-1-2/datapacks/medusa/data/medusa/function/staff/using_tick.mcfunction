advancement revoke @s only medusa:events/staff_using
scoreboard players set @s md_staff_seen 3
function medusa:staff/read_charges
execute if score @s md_use matches 0 if predicate medusa:is_sneaking if items entity @s weapon.offhand minecraft:scute[minecraft:custom_data~{md_item:"gorgon_scale"}] run function medusa:staff/recharge
execute if score @s md_use matches 0 if predicate medusa:is_sneaking unless items entity @s weapon.offhand minecraft:scute[minecraft:custom_data~{md_item:"gorgon_scale"}] run tag @s add md.staff_spikes_pending
execute if score @s md_use matches 0 if predicate medusa:is_sneaking run scoreboard players set @s md_use 1
execute if score @s md_use matches 0 unless predicate medusa:is_sneaking run function medusa:staff/start_use
execute if score @s md_use matches 1.. unless predicate medusa:is_sneaking if entity @s[tag=md.staff_session] run scoreboard players add @s md_use 1
