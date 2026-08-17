tag @s add fk.boss
attribute @s minecraft:max_health base set 160
attribute @s minecraft:attack_damage base set 0
attribute @s minecraft:scale base set 1.4
data merge entity @s {Health:160.0f,PersistenceRequired:1b,CanPickUpLoot:0b,Silent:1b,DeathLootTable:"fallen_knight:entity/empty"}
item replace entity @s weapon.mainhand with minecraft:iron_sword
item replace entity @s weapon.offhand with minecraft:shield
item replace entity @s armor.head with minecraft:netherite_helmet
item replace entity @s armor.chest with minecraft:netherite_chestplate
item replace entity @s armor.legs with minecraft:netherite_leggings
item replace entity @s armor.feet with minecraft:netherite_boots
scoreboard players set @s fk_phase 0
scoreboard players set @s fk_attack 0
scoreboard players set @s fk_timer 0
scoreboard players set @s fk_prev 0
