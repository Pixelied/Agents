function fallen_knight:debug/cleanup_test_fights
execute summon minecraft:marker run function fallen_knight:debug/bootstrap_test_arena
schedule function fallen_knight:debug/start_test_fight_finish 1t replace
tellraw @s [{"text":"[Fallen Knight] ","color":"dark_gray"},{"text":"Test fight is initializing. Stay within the arena; use /function fallen_knight:debug/cleanup_test_fights to reset.","color":"gray"}]
