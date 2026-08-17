$execute if score fk_win_$(eid) fk_result matches 1 if score @s fk_ptime matches 200.. run function fallen_knight:reward/player
$execute if score fk_win_$(eid) fk_result matches 1 if score @s fk_ptime matches ..199 run function fallen_knight:reward/clear_player
$execute if score fk_win_$(eid) fk_result matches -1 run function fallen_knight:reward/clear_player
