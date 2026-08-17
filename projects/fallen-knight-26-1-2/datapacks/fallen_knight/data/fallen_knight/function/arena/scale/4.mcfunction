scoreboard players set @s fk_maxhp 344
scoreboard players set @s fk_halfhp 172
scoreboard players set @s fk_joinhp 86
execute store result storage fallen_knight:macro arena.aid int 1 run scoreboard players get @s fk_aid
execute store result storage fallen_knight:macro arena.maxhp int 1 run scoreboard players get @s fk_maxhp
function fallen_knight:arena/apply_scaled_health with storage fallen_knight:macro arena
