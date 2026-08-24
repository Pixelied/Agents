# Called as the hurt Guarding Knight after arena-id validation in the shared hit hook.
function fallen_knight:boss/director/face_target
playsound minecraft:item.shield.block hostile @a[distance=..20] ~ ~ ~ 1 0.55
execute if score @s fk_cd_bash matches 0 run function fallen_knight:boss/attack/shield_bash/start
