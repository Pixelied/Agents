advancement revoke @s only medusa:events/staff_using
scoreboard players set @s md_staff_seen 3
function medusa:staff/read_charges
execute unless entity @s[tag=md.staff_blocked_until_release] if score @s md_use matches 0 if predicate medusa:is_sneaking if items entity @s weapon.offhand minecraft:scute[minecraft:custom_data~{md_item:"gorgon_scale"}] run function medusa:staff/recharge
execute unless entity @s[tag=md.staff_blocked_until_release] if score @s md_use matches 0 if predicate medusa:is_sneaking unless items entity @s weapon.offhand minecraft:scute[minecraft:custom_data~{md_item:"gorgon_scale"}] run tag @s add md.staff_spikes_pending
execute unless entity @s[tag=md.staff_blocked_until_release] if score @s md_use matches 0 if predicate medusa:is_sneaking run scoreboard players set @s md_use 1
execute unless entity @s[tag=md.staff_blocked_until_release] if score @s md_use matches 0 unless predicate medusa:is_sneaking run function medusa:staff/start_use
execute unless entity @s[tag=md.staff_blocked_until_release] if score @s md_use matches 1.. unless predicate medusa:is_sneaking if entity @s[tag=md.staff_session] unless entity @s[tag=md.staff_started_now] run function medusa:staff/channel/tick
tag @s remove md.staff_started_now
