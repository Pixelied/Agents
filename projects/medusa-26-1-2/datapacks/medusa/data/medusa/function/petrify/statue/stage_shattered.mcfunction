$data merge entity @e[type=minecraft:block_display,tag=md.statue_shell,scores={md_aid=$(aid)},limit=1] {block_state:{Name:"minecraft:cobblestone"}}
particle minecraft:block{block_state:{Name:"minecraft:cobblestone"}} ~ ~1 ~ 0.5 0.9 0.5 0.08 28 force
playsound minecraft:block.stone.break block @a[distance=..12] ~ ~1 ~ 0.9 1.05
