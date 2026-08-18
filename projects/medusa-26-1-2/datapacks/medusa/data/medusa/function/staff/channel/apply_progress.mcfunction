$execute as @e[scores={md_tid=$(lock)},limit=1] at @s run function medusa:staff/channel/apply_progress_target {tick:$(tick)}
