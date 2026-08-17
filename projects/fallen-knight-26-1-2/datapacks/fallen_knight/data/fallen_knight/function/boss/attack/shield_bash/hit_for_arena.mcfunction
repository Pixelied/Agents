$execute positioned ^ ^1 ^1.4 run damage @a[tag=fk.participant,scores={fk_aid=$(aid)},distance=..1.3] 5 fallen_knight:knight_slash by @s
$execute positioned ^ ^1 ^1.4 run effect give @a[tag=fk.participant,scores={fk_aid=$(aid)},distance=..1.3] minecraft:slowness 1 1 true
$execute positioned ^ ^1 ^1.4 as @a[tag=fk.participant,scores={fk_aid=$(aid)},distance=..1.3] at @s facing entity @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] feet run tp @s ^ ^ ^-1.1
