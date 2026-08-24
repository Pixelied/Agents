execute store result storage medusa:macro eye.eid int 1 run scoreboard players get @s md_eid
function medusa:arena/pedestal/remove with storage medusa:macro eye
function medusa:arena/pedestal/give_eye with storage medusa:macro eye
scoreboard players set @s md_eye_state 1
function medusa:arena/awakening/start
