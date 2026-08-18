scoreboard players add @s md_lock 0
scoreboard players set @s md_staff_hit 0
scoreboard players set @s md_ray 0
tag @s add md.staff_caster
execute anchored eyes positioned ^ ^ ^0.5 run function medusa:staff/target/raycast
tag @s remove md.staff_caster
