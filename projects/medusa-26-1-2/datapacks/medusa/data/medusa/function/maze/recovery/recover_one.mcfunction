execute store result storage medusa:macro recovery.eid int 1 run scoreboard players get @s md_eid
execute if score @s md_mphase matches 2..9 run function medusa:maze/recovery/cleanup_transient with storage medusa:macro recovery
execute if score @s md_mphase matches 2..9 run function medusa:maze/recovery/rebuild_committed with storage medusa:macro recovery
execute if score @s md_mphase matches 2..9 run scoreboard players set @s md_mtick 0
execute if score @s md_mphase matches 2..9 run scoreboard players set @s md_mtry 0
execute if score @s md_mphase matches 2..9 run scoreboard players set @s md_mdelta 0
execute if score @s md_mphase matches 2..9 run scoreboard players set @s md_mblocked 0
execute if score @s md_mphase matches 2..9 if score @s md_dungeon_clear matches 0 run scoreboard players set @s md_mphase 2
execute if score @s md_mphase matches 2..9 if score @s md_dungeon_clear matches 1.. run scoreboard players set @s md_mphase 9
execute if score @s md_mphase matches 2 if score @s md_dungeon_clear matches 0 run function medusa:maze/trap/rearm
