scoreboard players set @s md_state 3
function medusa:reward/distribute
function medusa:reward/return_eye
function medusa:arena/unseal
execute store result storage medusa:macro bossbar.eid int 1 run scoreboard players get @s md_eid
function medusa:boss/bossbar/remove with storage medusa:macro bossbar
scoreboard players set @s md_state 4
