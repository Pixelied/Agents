particle minecraft:falling_dust{block_state:{Name:"minecraft:cobbled_deepslate"}} ~ ~1 ~ 1.5 1.0 1.5 0.04 50 force
playsound minecraft:block.deepslate.break master @a[distance=..18] ~ ~1 ~ 1.0 0.7
effect give @a[gamemode=survival,distance=..4] minecraft:slow_falling 4 0 true
effect give @a[gamemode=adventure,distance=..4] minecraft:slow_falling 4 0 true
