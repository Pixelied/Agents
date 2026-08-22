execute if score @s md_mphase matches 1 run function medusa:maze/generate/tick
execute if score @s md_state matches 0 run function medusa:puzzle/tick_all
execute if score @s md_state matches 1 run function medusa:arena/awakening/tick
execute store result storage medusa:macro ctx.eid int 1 run scoreboard players get @s md_eid
execute if score @s md_state matches 1..2 run function medusa:instance/participants/late_join with storage medusa:macro ctx
execute if score @s md_state matches 1..2 run function medusa:instance/watchdog with storage medusa:macro ctx
execute if score @s md_state matches 2 run function medusa:arena/reset/check with storage medusa:macro ctx
