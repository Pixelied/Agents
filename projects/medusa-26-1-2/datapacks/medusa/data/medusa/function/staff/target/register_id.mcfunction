execute if data entity @s Health unless score @s md_tid matches 1.. run scoreboard players add $next_tid md_tid 1
execute if data entity @s Health unless score @s md_tid matches 1.. run scoreboard players operation @s md_tid = $next_tid md_tid
execute if data entity @s Health run tag @s add md.staff_candidate
