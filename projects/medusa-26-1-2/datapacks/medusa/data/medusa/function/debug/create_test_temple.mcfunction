forceload add -64 0 128 128
scoreboard players set $chunk_wait md_tmp 0
schedule function medusa:debug/wait_for_test_chunk 1t replace
