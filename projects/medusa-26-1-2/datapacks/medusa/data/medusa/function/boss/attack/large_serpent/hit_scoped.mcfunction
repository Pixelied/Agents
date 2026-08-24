$execute at @e[type=minecraft:marker,tag=md.large_serpent_hit,scores={md_eid=$(eid)},limit=1] run damage @a[tag=md.participant,scores={md_eid=$(eid)},distance=..3.2] 10 minecraft:mob_attack by @s
$execute at @e[type=minecraft:marker,tag=md.large_serpent_hit,scores={md_eid=$(eid)},limit=1] run particle minecraft:explosion ~ ~1 ~ 1.5 0.5 1.5 0 3 force
$kill @e[type=minecraft:marker,tag=md.large_serpent_hit,scores={md_eid=$(eid)}]
$kill @e[type=minecraft:block_display,tag=md.large_serpent_display,scores={md_eid=$(eid)}]
