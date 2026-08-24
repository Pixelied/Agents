scoreboard players set $rescue md_tmp 1
execute if items entity @s weapon.mainhand #minecraft:swords run scoreboard players set $rescue md_tmp 2
execute if items entity @s weapon.mainhand #minecraft:axes run scoreboard players set $rescue md_tmp 2
execute if items entity @s weapon.mainhand minecraft:mace run scoreboard players set $rescue md_tmp 2
execute if items entity @s weapon.mainhand #minecraft:pickaxes run scoreboard players set $rescue md_tmp 4
$scoreboard players operation @a[tag=md.petrified,scores={md_aid=$(owner)},limit=1] md_shell += $rescue md_tmp
$execute as @a[tag=md.petrified,scores={md_aid=$(owner)},limit=1] at @s run function medusa:petrify/statue/update_stage
