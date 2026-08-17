$execute as @e[tag=fk.boss,scores={fk_aid=$(aid),fk_attack=1,fk_timer=8..27},distance=..4,nbt={HurtTime:10s},limit=1] at @s run function fallen_knight:boss/attack/guard/counter
