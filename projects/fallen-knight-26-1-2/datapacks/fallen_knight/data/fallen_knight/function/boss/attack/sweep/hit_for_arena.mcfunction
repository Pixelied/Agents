$execute positioned ^-1.5 ^1 ^1.4 as @a[tag=fk.participant,scores={fk_aid=$(aid)},distance=..1.55] run tag @s add fk.sweep_hit
$execute positioned ^ ^1 ^1.8 as @a[tag=fk.participant,scores={fk_aid=$(aid)},distance=..1.55] run tag @s add fk.sweep_hit
$execute positioned ^1.5 ^1 ^1.4 as @a[tag=fk.participant,scores={fk_aid=$(aid)},distance=..1.55] run tag @s add fk.sweep_hit
$damage @a[tag=fk.sweep_hit,scores={fk_aid=$(aid)}] 7 fallen_knight:knight_slash by @s
tag @a remove fk.sweep_hit
