$execute as @a[tag=!fk.participant,gamemode=!spectator,scores={fk_alive=1..},distance=..12] run tag @s add fk.participant
$execute as @a[tag=fk.participant,scores={fk_aid=0},distance=..12] run scoreboard players set @s fk_aid $(aid)
$execute as @a[tag=fk.participant,scores={fk_aid=$(aid)},distance=..12] run scoreboard players set @s fk_eid $(eid)
$execute as @a[tag=fk.participant,scores={fk_aid=$(aid)},distance=..12] run scoreboard players set @s fk_ptime 0
