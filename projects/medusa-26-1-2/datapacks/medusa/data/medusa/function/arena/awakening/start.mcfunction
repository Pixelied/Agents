scoreboard players set @s md_state 1
scoreboard players set @s md_timer 0
function medusa:arena/seal
execute store result storage medusa:macro ctx.eid int 1 run scoreboard players get @s md_eid
function medusa:instance/participants/register_initial with storage medusa:macro ctx
playsound minecraft:block.beacon.deactivate master @a[distance=..48] ~64 ~-12 ~72 1.2 0.6
particle minecraft:smoke ~64 ~-11 ~72 2 4 2 0.03 50 force
