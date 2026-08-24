$execute if entity @a[tag=md.participant,scores={md_eid=$(eid)},distance=..1.3,limit=1] run function medusa:boss/attack/venom_spit/impact
$execute unless entity @a[tag=md.participant,scores={md_eid=$(eid)},distance=..1.3,limit=1] facing entity @a[tag=md.participant,scores={md_eid=$(eid)},sort=nearest,limit=1] eyes run tp @s ^ ^ ^0.65
