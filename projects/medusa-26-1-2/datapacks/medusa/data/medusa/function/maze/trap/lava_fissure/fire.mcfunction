particle minecraft:falling_lava ~ ~1 ~ 2.0 0.5 2.0 0.03 70 force
playsound minecraft:block.lava.pop master @a[distance=..18] ~ ~1 ~ 1.0 0.8
effect give @a[gamemode=survival,distance=..4] minecraft:slowness 3 1 true
effect give @a[gamemode=adventure,distance=..4] minecraft:slowness 3 1 true
