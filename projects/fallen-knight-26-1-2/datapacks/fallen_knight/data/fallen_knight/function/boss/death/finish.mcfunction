execute store result storage fallen_knight:macro result.eid int 1 run scoreboard players get @s fk_eid
function fallen_knight:arena/result/victory with storage fallen_knight:macro result
function fallen_knight:reward/distribute
function fallen_knight:arena/mark_cleared
kill @s
