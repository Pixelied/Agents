$execute as @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run scoreboard players operation @s fk_maxhp = @e[tag=fk.arena,scores={fk_aid=$(aid)},limit=1] fk_maxhp
$execute as @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run scoreboard players operation @s fk_halfhp = @e[tag=fk.arena,scores={fk_aid=$(aid)},limit=1] fk_halfhp
$execute as @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run scoreboard players operation @s fk_joinhp = @e[tag=fk.arena,scores={fk_aid=$(aid)},limit=1] fk_joinhp
$execute as @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run attribute @s minecraft:max_health base set $(maxhp)
$execute as @e[tag=fk.boss,scores={fk_aid=$(aid)},limit=1] run data modify entity @s Health set value $(maxhp)f
