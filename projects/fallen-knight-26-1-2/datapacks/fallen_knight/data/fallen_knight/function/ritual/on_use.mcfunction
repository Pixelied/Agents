advancement revoke @s only fallen_knight:events/knights_oath_used
loot give @s loot fallen_knight:items/knights_oath
execute at @s if entity @e[type=minecraft:marker,tag=fk.arena,scores={fk_state=2},sort=nearest,limit=1,distance=..4] run function fallen_knight:ritual/check_offering
execute at @s unless entity @e[type=minecraft:marker,tag=fk.arena,scores={fk_state=2},sort=nearest,limit=1,distance=..4] run title @s actionbar {"text":"The oath answers only at a cleared Fallen Knight altar.","color":"dark_gray"}
