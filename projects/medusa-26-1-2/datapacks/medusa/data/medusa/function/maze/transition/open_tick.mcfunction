scoreboard players add @s md_mtick 1
execute if score @s md_mtick matches 1 run function medusa:maze/transition/open_apply
execute if score @s md_mtick matches 4.. run function medusa:maze/transition/start_close
