$execute positioned ^ ^1 ^1.0 as @a[tag=fk.participant,tag=!fk.charge_hit,scores={fk_aid=$(aid)},distance=..1.2] run tag @s add fk.charge_hit
$damage @a[tag=fk.charge_hit,scores={fk_aid=$(aid)}] 6 fallen_knight:knight_slash by @s
$execute as @a[tag=fk.charge_hit,scores={fk_aid=$(aid)}] at @s facing entity @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] feet run tp @s ^ ^ ^-0.8
