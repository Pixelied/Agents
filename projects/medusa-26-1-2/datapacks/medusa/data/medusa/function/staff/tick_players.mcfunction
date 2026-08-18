scoreboard players add @a md_staff_seen 0
scoreboard players remove @a[scores={md_staff_seen=1..}] md_staff_seen 1
execute as @a if items entity @s weapon.mainhand minecraft:breeze_rod[minecraft:custom_data~{md_item:"medusa_staff"}] run function medusa:staff/read_charges
execute as @a[scores={md_use=1..,md_staff_seen=..0}] run function medusa:staff/end_use
execute as @a[tag=md.staff_blocked_until_release,scores={md_staff_seen=..0}] run function medusa:staff/end_use
execute as @e[tag=md.staff_petrified] at @s run function medusa:staff/target/tick_petrification
