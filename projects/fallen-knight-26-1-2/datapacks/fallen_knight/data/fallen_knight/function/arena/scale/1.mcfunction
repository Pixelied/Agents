scoreboard players set @s fk_maxhp 160
scoreboard players set @s fk_halfhp 80
scoreboard players set @s fk_joinhp 40
execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
execute store result storage fallen_knight:macro arena.maxhp int 1 run scoreboard players get @s fk_maxhp
function fallen_knight:arena/apply_scaled_health with storage fallen_knight:macro arena
