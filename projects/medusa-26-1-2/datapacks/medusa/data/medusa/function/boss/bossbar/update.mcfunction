$bossbar set medusa:arena_$(eid) max $(maxhp)
$bossbar set medusa:arena_$(eid) value $(hp)
$bossbar set medusa:arena_$(eid) players @a[tag=md.participant,scores={md_eid=$(eid)}]
