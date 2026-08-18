tag @s add md.staff_caster
scoreboard players set @s md_ray 0
execute anchored eyes positioned ^ ^ ^0.5 run function medusa:staff/quick/ray
tag @s remove md.staff_caster
