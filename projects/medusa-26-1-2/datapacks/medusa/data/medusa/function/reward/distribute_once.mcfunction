scoreboard players set @s md_rewarded 1
scoreboard players set @s md_killed 1
loot spawn ~64 ~-16 ~72 loot medusa:rewards/medusa_kill
execute store result storage medusa:macro reward.eid int 1 run scoreboard players get @s md_eid
function medusa:reward/grant_xp with storage medusa:macro reward
