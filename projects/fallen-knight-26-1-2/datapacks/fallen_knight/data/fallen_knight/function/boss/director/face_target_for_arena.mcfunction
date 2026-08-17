$execute if entity @a[tag=fk.participant,scores={fk_aid=$(aid)},distance=..20] run tp @s ~ ~ ~ facing entity @a[tag=fk.participant,scores={fk_aid=$(aid)},sort=nearest,limit=1,distance=..20] eyes
