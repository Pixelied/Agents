forceload add 0 0 96 96
forceload add -48 0 -1 96
scoreboard players set $chunk_wait md_tmp 0
schedule function medusa:debug/wait_for_test_chunk 1t replace
