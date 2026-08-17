$execute positioned ^ ^1 ^1.0 as @a[tag=fk.participant,tag=!fk.lunge_hit,scores={fk_aid=$(aid)},distance=..1.2] run tag @s add fk.lunge_hit
$damage @a[tag=fk.lunge_hit,scores={fk_aid=$(aid)}] 8 fallen_knight:knight_heavy by @s
