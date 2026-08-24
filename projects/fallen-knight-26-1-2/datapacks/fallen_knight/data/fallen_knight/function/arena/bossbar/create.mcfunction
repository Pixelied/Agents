$bossbar remove fallen_knight:arena_$(aid)
$bossbar add fallen_knight:arena_$(aid) {"text":"The Fallen Knight","color":"gray","bold":true}
$bossbar set fallen_knight:arena_$(aid) max $(maxhp)
$bossbar set fallen_knight:arena_$(aid) value $(maxhp)
$bossbar set fallen_knight:arena_$(aid) players @a[tag=fk.participant,scores={fk_aid=$(aid)}]
$bossbar set fallen_knight:arena_$(aid) visible true
