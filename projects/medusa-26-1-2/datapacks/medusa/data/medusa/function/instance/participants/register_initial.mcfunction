execute if score @s md_state matches 1..2 positioned ~48 ~-17 ~56 as @a[dx=32,dy=25,dz=32] unless score @s md_aid matches 1.. run function medusa:instance/participants/assign_actor
$execute if score @s md_state matches 1..2 positioned ~48 ~-17 ~56 as @a[dx=32,dy=25,dz=32] run scoreboard players set @s md_eid $(eid)
execute if score @s md_state matches 1..2 positioned ~48 ~-17 ~56 as @a[dx=32,dy=25,dz=32] run tag @s add md.participant
