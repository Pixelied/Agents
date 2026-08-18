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
