particle minecraft:witch ~ ~1.5 ~ 1.5 1.0 1.5 0.02 45 force
playsound minecraft:block.amethyst_block.resonate master @a[distance=..18] ~ ~1 ~ 0.8 0.7
scoreboard players add @a[gamemode=survival,distance=..5] md_petr 120
scoreboard players add @a[gamemode=adventure,distance=..5] md_petr 120
execute as @a[gamemode=survival,distance=..5] run function medusa:gaze/apply_thresholds
execute as @a[gamemode=adventure,distance=..5] run function medusa:gaze/apply_thresholds
