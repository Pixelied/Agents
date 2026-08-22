execute unless score @s md_mmode matches 90 unless score @s md_mdelta matches 16..28 run function medusa:maze/validate/reject
execute unless score @s md_mmode matches 90 if score @s md_mdelta matches 16..28 run function medusa:maze/warning/start
execute if score @s md_mmode matches 90 run function medusa:maze/generate/validate_initial/accept
