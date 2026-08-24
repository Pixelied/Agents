scoreboard players add $chunk_wait md_tmp 1
scoreboard players set $chunk_ready md_tmp 0
execute positioned 0 110 0 run summon minecraft:marker ~ ~ ~ {Tags:["md.chunk_probe"]}
execute if entity @e[type=minecraft:marker,tag=md.chunk_probe,limit=1] run scoreboard players set $chunk_ready md_tmp 1
kill @e[type=minecraft:marker,tag=md.chunk_probe]
execute if score $chunk_ready md_tmp matches 1 run schedule function medusa:debug/create_test_temple_loaded 1t replace
execute if score $chunk_ready md_tmp matches 0 if score $chunk_wait md_tmp matches ..199 run schedule function medusa:debug/wait_for_test_chunk 1t replace
execute if score $chunk_ready md_tmp matches 0 if score $chunk_wait md_tmp matches 200.. run say MEDUSA_CHUNK_READY_FAILED
