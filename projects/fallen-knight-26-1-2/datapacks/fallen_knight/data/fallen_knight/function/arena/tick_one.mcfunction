# Dormant arenas begin when at least one living non-spectator enters the inner courtyard.
execute if score @s fk_state matches 0 if entity @a[tag=!fk.participant,gamemode=!spectator,scores={fk_alive=1..},distance=..12] run function fallen_knight:arena/start

# Active arena snapshots current boss health and controls late-join/reset windows.
execute if score @s fk_state matches 1 run function fallen_knight:arena/check_boss_bounds
execute if score @s fk_state matches 1 store result score @s fk_hp run data get entity @e[tag=fk.boss,sort=nearest,limit=1,distance=..13] Health 1
execute if score @s fk_state matches 1 if score @s fk_hp > @s fk_joinhp run scoreboard players set @s fk_join 1
execute if score @s fk_state matches 1 unless score @s fk_hp > @s fk_joinhp run scoreboard players set @s fk_join 0
execute if score @s fk_state matches 1 if score @s fk_join matches 1 run function fallen_knight:arena/participants/late_join_scan

execute if score @s fk_state matches 1 unless entity @a[tag=fk.participant,gamemode=!spectator,scores={fk_alive=1..},distance=..13] run scoreboard players add @s fk_reset 1
execute if score @s fk_state matches 1 if entity @a[tag=fk.participant,gamemode=!spectator,scores={fk_alive=1..},distance=..13] run scoreboard players set @s fk_reset 0
execute if score @s fk_state matches 1 if score @s fk_reset matches 300.. run function fallen_knight:arena/reset

# Keep the arena-local bar synchronized with the living boss.
execute if score @s fk_state matches 1 run execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
execute if score @s fk_state matches 1 run execute store result storage fallen_knight:macro arena.hp int 1 run scoreboard players get @s fk_hp
execute if score @s fk_state matches 1 run function fallen_knight:arena/bossbar/update with storage fallen_knight:macro arena
