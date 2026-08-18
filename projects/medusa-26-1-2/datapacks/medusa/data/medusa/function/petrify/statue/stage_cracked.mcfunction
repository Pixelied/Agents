$data merge entity @e[type=minecraft:block_display,tag=md.statue_shell,scores={md_aid=$(aid)},limit=1] {block_state:{Name:"minecraft:cracked_stone_bricks"}}
particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~1 ~ 0.4 0.8 0.4 0.05 18 force
playsound minecraft:block.stone.break block @a[distance=..12] ~ ~1 ~ 0.7 0.8
