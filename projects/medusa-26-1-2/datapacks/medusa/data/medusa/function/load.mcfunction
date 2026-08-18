scoreboard objectives add md_eid dummy
scoreboard objectives add md_aid dummy
scoreboard objectives add md_tid dummy
scoreboard objectives add md_state dummy
scoreboard objectives add md_phase dummy
scoreboard objectives add md_timer dummy
scoreboard objectives add md_tmp dummy
scoreboard objectives add md_count dummy
scoreboard objectives add md_hp dummy
scoreboard objectives add md_maxhp dummy
scoreboard objectives add md_p2hp dummy
scoreboard objectives add md_p3hp dummy
scoreboard objectives add md_attack dummy
scoreboard objectives add md_cd dummy
scoreboard objectives add md_petr dummy
scoreboard objectives add md_pct dummy
scoreboard objectives add md_decay dummy
scoreboard objectives add md_shell dummy
scoreboard objectives add md_grace dummy
scoreboard objectives add md_staff dummy
scoreboard objectives add md_lock dummy
scoreboard objectives add md_use dummy
scoreboard objectives add md_rewarded dummy
scoreboard players add $next_eid md_eid 0
scoreboard players add $next_aid md_aid 0
scoreboard players add $next_tid md_tid 0
schedule function medusa:instance/recover_loaded 1t replace
scoreboard objectives add md_p1_done dummy
scoreboard objectives add md_p2_done dummy
scoreboard objectives add md_p3_done dummy
scoreboard objectives add md_dungeon_clear dummy
scoreboard objectives add md_p1_o1 dummy
scoreboard objectives add md_p1_o2 dummy
scoreboard objectives add md_p1_o3 dummy
scoreboard objectives add md_p1_b1 dummy
scoreboard objectives add md_p1_b2 dummy
scoreboard objectives add md_p1_b3 dummy
scoreboard objectives add md_p1_submit dummy
scoreboard objectives add md_p2_left dummy
scoreboard objectives add md_p2_right dummy
scoreboard objectives add md_p2_bl dummy
scoreboard objectives add md_p2_br dummy
scoreboard objectives add md_p3_timer dummy
scoreboard objectives add md_p3_zone dummy
scoreboard objectives add md_eye_state dummy
scoreboard players set $75 md_tmp 75
scoreboard players set $60 md_tmp 60
scoreboard players set $28 md_tmp 28
scoreboard players set $100 md_tmp 100
scoreboard objectives add md_gaze_tick dummy
scoreboard objectives add md_gaze_hit dummy
scoreboard objectives add md_ray dummy
scoreboard objectives add md_gorgon_active dummy
scoreboard players set $10 md_tmp 10
