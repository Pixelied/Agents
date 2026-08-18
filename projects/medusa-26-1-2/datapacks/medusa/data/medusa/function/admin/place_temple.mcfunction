summon minecraft:marker ~ ~ ~ {Tags:["md.instance","md.new_instance"]}
execute as @e[type=minecraft:marker,tag=md.new_instance,distance=..2,limit=1,sort=nearest] at @s run function medusa:instance/register
