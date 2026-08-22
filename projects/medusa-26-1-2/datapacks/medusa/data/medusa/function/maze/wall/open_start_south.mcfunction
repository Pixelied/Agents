fill ~-1 ~1 ~3 ~1 ~7 ~4 minecraft:barrier
function medusa:maze/wall/spawn_display {x:-1,z:3,sx:1,sz:2,ystart:0}
function medusa:maze/wall/spawn_display {x:0,z:3,sx:1,sz:2,ystart:0}
function medusa:maze/wall/spawn_display {x:1,z:3,sx:1,sz:2,ystart:0}
function medusa:maze/wall/spawn_controller {mode:1}
execute as @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1,limit=1,sort=nearest] run function medusa:maze/wall/animate_open
execute as @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1] run tag @s remove md.maze.new_wall_controller
