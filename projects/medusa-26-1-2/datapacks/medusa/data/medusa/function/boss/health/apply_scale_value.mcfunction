$attribute @e[type=minecraft:husk,tag=md.boss,scores={md_eid=$(eid)},limit=1] minecraft:max_health base set $(maxhp)
$data modify entity @e[type=minecraft:husk,tag=md.boss,scores={md_eid=$(eid)},limit=1] Health set value $(maxhp)f
$scoreboard players set @e[type=minecraft:husk,tag=md.boss,scores={md_eid=$(eid)},limit=1] md_maxhp $(maxhp)
$scoreboard players set @e[type=minecraft:husk,tag=md.boss,scores={md_eid=$(eid)},limit=1] md_p2hp $(p2hp)
$scoreboard players set @e[type=minecraft:husk,tag=md.boss,scores={md_eid=$(eid)},limit=1] md_p3hp $(p3hp)
