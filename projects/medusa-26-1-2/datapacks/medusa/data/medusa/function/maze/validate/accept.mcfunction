execute unless score @s md_mdelta matches 16..28 run function medusa:maze/validate/reject
execute if score @s md_mdelta matches 16..28 run scoreboard players set @s md_mphase 5
execute if score @s md_mdelta matches 16..28 run scoreboard players set @s md_mtick 0
