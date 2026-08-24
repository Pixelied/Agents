function medusa:debug/start_test_boss
execute if entity @e[tag=md.boss,limit=1] run say MEDUSA_BOSS_OK
execute unless entity @e[tag=md.boss,limit=1] run say MEDUSA_BOSS_MISSING
function medusa:debug/give_test_items
function medusa:debug/test_petrification_damage
function medusa:debug/test_gaze_pipeline
function medusa:debug/test_staff_runtime
function medusa:debug/test_lifecycle
say MEDUSA_SMOKE_DONE
