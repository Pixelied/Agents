summon minecraft:marker ~ ~ ~ {Tags:["md.venom_hazard","md.new_venom_hazard"]}
scoreboard players operation @e[type=minecraft:marker,tag=md.new_venom_hazard,distance=..2,limit=1,sort=nearest] md_eid = @s md_eid
scoreboard players set @e[type=minecraft:marker,tag=md.new_venom_hazard,distance=..2,limit=1,sort=nearest] md_timer 0
tag @e[type=minecraft:marker,tag=md.new_venom_hazard,distance=..2] remove md.new_venom_hazard
particle minecraft:spore_blossom_air ~ ~0.2 ~ 1.5 0.2 1.5 0.03 30 force
playsound minecraft:block.slime_block.break block @a[distance=..24] ~ ~ ~ 0.8 0.7
kill @s
