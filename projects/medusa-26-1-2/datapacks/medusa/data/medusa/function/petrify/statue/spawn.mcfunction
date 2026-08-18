execute store result storage medusa:macro stone.aid int 1 run scoreboard players get @s md_aid
function medusa:petrify/statue/clear_helpers with storage medusa:macro stone
summon minecraft:block_display ~ ~ ~ {Tags:["md.statue_shell","md.new_statue_shell"],block_state:{Name:"minecraft:stone"},transformation:{translation:[-0.5f,0.0f,-0.5f],left_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[1.0f,1.9f,1.0f],right_rotation:[0.0f,0.0f,0.0f,1.0f]}}
scoreboard players operation @e[type=minecraft:block_display,tag=md.new_statue_shell,distance=..2,limit=1,sort=nearest] md_aid = @s md_aid
tag @e[type=minecraft:block_display,tag=md.new_statue_shell,distance=..2] remove md.new_statue_shell
summon minecraft:interaction ~ ~ ~ {Tags:["md.statue_hitbox","md.new_statue_hitbox"],width:1.0f,height:1.9f,response:1b}
scoreboard players operation @e[type=minecraft:interaction,tag=md.new_statue_hitbox,distance=..2,limit=1,sort=nearest] md_aid = @s md_aid
tag @e[type=minecraft:interaction,tag=md.new_statue_hitbox,distance=..2] remove md.new_statue_hitbox
particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~1 ~ 0.4 0.8 0.4 0.03 30 force
