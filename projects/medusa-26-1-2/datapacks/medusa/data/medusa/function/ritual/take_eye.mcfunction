execute store result storage medusa:macro eye.eid int 1 run scoreboard players get @s md_eid
function medusa:arena/pedestal/remove with storage medusa:macro eye
loot give @a[tag=md.eye_interactor,limit=1] loot medusa:items/golden_gorgon_eye
scoreboard players set @s md_eye_state 1
playsound minecraft:item.armor.equip_turtle master @a[tag=md.eye_interactor,limit=1] ~ ~ ~ 0.8 1.2
