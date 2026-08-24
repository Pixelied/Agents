execute store result storage medusa:macro ctx.eid int 1 run scoreboard players get @s md_eid
function medusa:instance/participants/register_initial with storage medusa:macro ctx
scoreboard players set @s md_build 0
