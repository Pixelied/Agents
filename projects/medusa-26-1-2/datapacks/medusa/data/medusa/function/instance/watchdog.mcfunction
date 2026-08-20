$execute as @a[tag=md.participant,scores={md_eid=$(eid)},gamemode=spectator] run function medusa:instance/participants/clear_player
$execute positioned ~64 ~-17 ~72 as @a[tag=md.participant,scores={md_eid=$(eid)},gamemode=!spectator] unless entity @s[distance=..30] run function medusa:instance/participants/clear_player
