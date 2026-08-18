summon minecraft:block_display ~ ~ ~ {Tags:["md.staff_spike","md.new_staff_spike"],block_state:{Name:"minecraft:pointed_dripstone",Properties:{vertical_direction:"up",thickness:"tip",waterlogged:"false"}},transformation:{translation:[-0.35f,0.0f,-0.35f],scale:[0.7f,2.4f,0.7f]}}
summon minecraft:block_display ~1.1 ~ ~ {Tags:["md.staff_spike","md.new_staff_spike"],block_state:{Name:"minecraft:pointed_dripstone",Properties:{vertical_direction:"up",thickness:"tip",waterlogged:"false"}},transformation:{translation:[-0.3f,0.0f,-0.3f],scale:[0.6f,1.9f,0.6f]}}
summon minecraft:block_display ~-1.1 ~ ~ {Tags:["md.staff_spike","md.new_staff_spike"],block_state:{Name:"minecraft:pointed_dripstone",Properties:{vertical_direction:"up",thickness:"tip",waterlogged:"false"}},transformation:{translation:[-0.3f,0.0f,-0.3f],scale:[0.6f,1.9f,0.6f]}}
summon minecraft:block_display ~ ~ ~1.1 {Tags:["md.staff_spike","md.new_staff_spike"],block_state:{Name:"minecraft:pointed_dripstone",Properties:{vertical_direction:"up",thickness:"tip",waterlogged:"false"}},transformation:{translation:[-0.3f,0.0f,-0.3f],scale:[0.6f,1.7f,0.6f]}}
summon minecraft:block_display ~ ~ ~-1.1 {Tags:["md.staff_spike","md.new_staff_spike"],block_state:{Name:"minecraft:pointed_dripstone",Properties:{vertical_direction:"up",thickness:"tip",waterlogged:"false"}},transformation:{translation:[-0.3f,0.0f,-0.3f],scale:[0.6f,1.7f,0.6f]}}
summon minecraft:block_display ~0.8 ~ ~0.8 {Tags:["md.staff_spike","md.new_staff_spike"],block_state:{Name:"minecraft:pointed_dripstone",Properties:{vertical_direction:"up",thickness:"tip",waterlogged:"false"}},transformation:{translation:[-0.25f,0.0f,-0.25f],scale:[0.5f,1.4f,0.5f]}}
summon minecraft:block_display ~-0.8 ~ ~-0.8 {Tags:["md.staff_spike","md.new_staff_spike"],block_state:{Name:"minecraft:pointed_dripstone",Properties:{vertical_direction:"up",thickness:"tip",waterlogged:"false"}},transformation:{translation:[-0.25f,0.0f,-0.25f],scale:[0.5f,1.4f,0.5f]}}
scoreboard players set @e[type=minecraft:block_display,tag=md.new_staff_spike,distance=..4] md_timer 0
tag @e[type=minecraft:block_display,tag=md.new_staff_spike,distance=..4] remove md.new_staff_spike
particle minecraft:block{block_state:{Name:"minecraft:stone"}} ~ ~0.5 ~ 1.5 0.4 1.5 0.08 35 force
playsound minecraft:block.pointed_dripstone.place master @a[distance=..24] ~ ~ ~ 1.0 0.65
function medusa:staff/spikes/hit
