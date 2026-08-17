$execute as @a[tag=fk.participant,gamemode=!spectator,scores={fk_aid=$(aid),fk_eid=$(eid),fk_ptime=200..,fk_alive=1..}] run function fallen_knight:reward/player
$execute as @a[tag=fk.participant,scores={fk_aid=$(aid),fk_eid=$(eid),fk_ptime=..199}] run function fallen_knight:reward/clear_player
