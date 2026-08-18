$bossbar remove medusa:arena_$(eid)
$bossbar add medusa:arena_$(eid) {"text":"Medusa","color":"green","bold":true}
$bossbar set medusa:arena_$(eid) max $(maxhp)
$bossbar set medusa:arena_$(eid) value $(maxhp)
$bossbar set medusa:arena_$(eid) players @a[tag=md.participant,scores={md_eid=$(eid)}]
$bossbar set medusa:arena_$(eid) visible true
