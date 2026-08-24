execute if score @s fk_first matches 1.. run function fallen_knight:reward/repeat
execute if score @s fk_first matches 0 run function fallen_knight:reward/first
function fallen_knight:reward/clear_player
