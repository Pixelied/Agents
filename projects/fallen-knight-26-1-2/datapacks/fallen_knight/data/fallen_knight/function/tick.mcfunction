scoreboard players add @a fk_aid 0
scoreboard players add @a fk_eid 0
scoreboard players add @a fk_ptime 0
scoreboard players add @a fk_first 0
scoreboard players add @a fk_swing 0
scoreboard players add @a[scores={fk_swing=..19}] fk_swing 1
scoreboard players add @a fk_cleave 0
scoreboard players remove @a[scores={fk_cleave=1..}] fk_cleave 1
function fallen_knight:reward/pending_tick
execute as @a[tag=fk.participant,scores={fk_eid=1..}] run function fallen_knight:arena/watchdog
function fallen_knight:arena/tick_all
function fallen_knight:arena/participants/tick_all
function fallen_knight:boss/tick_all
