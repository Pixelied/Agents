# Up to 16 randomized looped candidates are allowed. Attempt 16 is a loop-free DFS tree fallback,
# which cannot shorten the corner-to-corner route below the 24-cell Manhattan minimum.
execute if score @s md_mgen_try matches ..15 if score @s md_mtry matches 0 store result score @s md_mtry run random value 18..30
execute if score @s md_mgen_try matches ..15 if score @s md_mtick < @s md_mtry run function medusa:maze/generate/add_loop_step
execute if score @s md_mgen_try matches ..15 if score @s md_mtick < @s md_mtry run function medusa:maze/generate/add_loop_step
execute if score @s md_mgen_try matches ..15 if score @s md_mtick < @s md_mtry run function medusa:maze/generate/add_loop_step
execute if score @s md_mgen_try matches ..15 if score @s md_mtick < @s md_mtry run function medusa:maze/generate/add_loop_step
execute if score @s md_mgen_try matches ..15 if score @s md_mtick >= @s md_mtry run function medusa:maze/generate/validate_initial/start
execute if score @s md_mgen_try matches 16.. run function medusa:maze/generate/validate_initial/start
