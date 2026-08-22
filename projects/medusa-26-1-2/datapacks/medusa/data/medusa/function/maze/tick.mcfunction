execute if score @s md_mphase matches 2..7 run function medusa:maze/activity/check_players
# Descending phase order prevents a phase transition from executing its next phase in the same game tick.
execute if score @s md_mphase matches 8 run function medusa:maze/transition/commit
execute if score @s md_mphase matches 7 if score @s md_tmp matches 1 run function medusa:maze/transition/close_tick
execute if score @s md_mphase matches 6 if score @s md_tmp matches 1 run function medusa:maze/transition/open_tick
execute if score @s md_mphase matches 5 if score @s md_tmp matches 1 run function medusa:maze/warning/tick
execute if score @s md_mphase matches 4 if score @s md_tmp matches 1 run function medusa:maze/validate/tick
execute if score @s md_mphase matches 3 if score @s md_tmp matches 1 run function medusa:maze/propose/mutate
execute if score @s md_mphase matches 2 if score @s md_tmp matches 1 run scoreboard players add @s md_mtick 1
execute if score @s md_mphase matches 2 if score @s md_tmp matches 1 if score @s md_mtick matches 460.. run scoreboard players set @s md_mtry 0
execute if score @s md_mphase matches 2 if score @s md_tmp matches 1 if score @s md_mtick matches 460.. run function medusa:maze/propose/start
execute if score @s md_mphase matches 1 run function medusa:maze/generate/tick
execute if score @s md_mphase matches 0 run function medusa:maze/setup/tick
