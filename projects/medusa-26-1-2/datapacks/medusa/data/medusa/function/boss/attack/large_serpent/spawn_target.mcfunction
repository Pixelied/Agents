$execute at @a[tag=md.participant,scores={md_eid=$(eid)},sort=nearest,limit=1] run summon minecraft:marker ~ ~ ~ {Tags:["md.large_serpent_hit","md.new_serpent_hit"]}
$scoreboard players set @e[type=minecraft:marker,tag=md.new_serpent_hit,limit=1] md_eid $(eid)
tag @e[type=minecraft:marker,tag=md.new_serpent_hit,limit=1] remove md.new_serpent_hit
$execute at @a[tag=md.participant,scores={md_eid=$(eid)},sort=nearest,limit=1] run summon minecraft:block_display ~ ~-0.5 ~ {Tags:["md.large_serpent_display","md.new_serpent_display"],block_state:{Name:"minecraft:green_terracotta"},transformation:{translation:[-0.75f,0.0f,-2.5f],scale:[1.5f,1.2f,5.0f]}}
$scoreboard players set @e[type=minecraft:block_display,tag=md.new_serpent_display,limit=1] md_eid $(eid)
tag @e[type=minecraft:block_display,tag=md.new_serpent_display,limit=1] remove md.new_serpent_display
$execute at @e[type=minecraft:marker,tag=md.large_serpent_hit,scores={md_eid=$(eid)},limit=1] run particle minecraft:dust{color:[0.1f,0.45f,0.1f],scale:1.5f} ~ ~0.5 ~ 2.5 0.5 2.5 0.01 40 force
