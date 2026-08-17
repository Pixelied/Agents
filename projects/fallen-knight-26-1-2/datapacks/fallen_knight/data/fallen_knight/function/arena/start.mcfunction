scoreboard players set $count fk_tmp 0
execute as @a[tag=!fk.participant,gamemode=!spectator,scores={fk_alive=1..},distance=..12] run scoreboard players add $count fk_tmp 1
scoreboard players operation @s fk_count = $count fk_tmp
execute if score @s fk_count matches 0 run return 0

execute if score @s fk_count matches 1 run function fallen_knight:arena/scale/1
execute if score @s fk_count matches 2 run function fallen_knight:arena/scale/2
execute if score @s fk_count matches 3 run function fallen_knight:arena/scale/3
execute if score @s fk_count matches 4 run function fallen_knight:arena/scale/4
execute if score @s fk_count matches 5 run function fallen_knight:arena/scale/5
execute if score @s fk_count matches 6 run function fallen_knight:arena/scale/6
execute if score @s fk_count matches 7 run function fallen_knight:arena/scale/7
execute if score @s fk_count matches 8.. run function fallen_knight:arena/scale/8plus

scoreboard players add $nextenc fk_eid 1
scoreboard players operation @s fk_eid = $nextenc fk_eid
execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
execute store result storage fallen_knight:macro arena.eid int 1 run scoreboard players get @s fk_eid
execute store result storage fallen_knight:macro arena.maxhp int 1 run scoreboard players get @s fk_maxhp
function fallen_knight:arena/copy_encounter_to_boss with storage fallen_knight:macro arena
function fallen_knight:arena/participants/register_initial with storage fallen_knight:macro arena
function fallen_knight:arena/seal
function fallen_knight:arena/activate_boss with storage fallen_knight:macro arena
function fallen_knight:arena/bossbar/create with storage fallen_knight:macro arena
scoreboard players set @s fk_state 1
scoreboard players set @s fk_join 1
scoreboard players set @s fk_reset 0
