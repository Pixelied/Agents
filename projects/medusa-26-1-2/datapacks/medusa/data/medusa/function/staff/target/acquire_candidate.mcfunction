scoreboard players operation $candidate md_tmp = @e[tag=md.staff_candidate,distance=..0.8,limit=1,sort=nearest] md_tid
execute if score @s md_lock matches 0 run scoreboard players operation @s md_lock = $candidate md_tmp
execute if score @s md_lock = $candidate md_tmp run scoreboard players set @s md_staff_hit 1
execute unless score @s md_lock = $candidate md_tmp run scoreboard players set @s md_staff_hit 2
tag @e[tag=md.staff_candidate,distance=..0.8,limit=1,sort=nearest] remove md.staff_candidate
