$kill @e[type=minecraft:husk,tag=md.boss,scores={md_eid=$(eid)}]
$kill @e[tag=md.snake,scores={md_eid=$(eid)}]
$kill @e[type=minecraft:marker,tag=md.venom_projectile,scores={md_eid=$(eid)}]
$kill @e[type=minecraft:marker,tag=md.venom_hazard,scores={md_eid=$(eid)}]
$kill @e[type=minecraft:marker,tag=md.large_serpent_hit,scores={md_eid=$(eid)}]
$kill @e[type=minecraft:block_display,tag=md.large_serpent_display,scores={md_eid=$(eid)}]
$execute as @a[tag=md.participant,scores={md_eid=$(eid)}] run function medusa:instance/participants/clear_player
$kill @e[type=minecraft:block_display,tag=md.statue_shell,scores={md_eid=$(eid)}]
$kill @e[type=minecraft:interaction,tag=md.statue_hitbox,scores={md_eid=$(eid)}]
$bossbar remove medusa:arena_$(eid)
