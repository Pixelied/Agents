scoreboard players set @s md_dungeon_clear 1
scoreboard players set @s md_mphase 9
scoreboard players set @s md_mtick 0
scoreboard players set @s md_mtry 0
scoreboard players set @s md_mdelta 0
function medusa:maze/wall/cleanup
function medusa:maze/trap/cleanup
execute store result storage medusa:macro completion.eid int 1 run scoreboard players get @s md_eid
function medusa:maze/completion/complete_ctx with storage medusa:macro completion
playsound minecraft:block.amethyst_block.resonate master @a[distance=..128] ~ ~ ~ 1.0 0.65
