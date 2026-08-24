execute store result storage medusa:macro reset.eid int 1 run scoreboard players get @s md_eid
function medusa:arena/reset/cleanup_scoped with storage medusa:macro reset
function medusa:dungeon/restore_cover
function medusa:arena/unseal
execute if score @s md_ritual_paid matches 1 run function medusa:ritual/refund_pending
scoreboard players set @s md_phase 0
scoreboard players set @s md_timer 0
scoreboard players set @s md_reset 0
function medusa:reward/return_eye
execute if score @s md_killed matches 1.. run scoreboard players set @s md_state 4
execute unless score @s md_killed matches 1.. run scoreboard players set @s md_state 0
