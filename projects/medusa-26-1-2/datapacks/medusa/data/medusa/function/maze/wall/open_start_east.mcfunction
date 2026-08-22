fill ~3 ~1 ~-1 ~4 ~7 ~1 minecraft:barrier
function medusa:maze/wall/spawn_display {x:3,z:-1,sx:2,sz:1,ystart:0}
function medusa:maze/wall/spawn_display {x:3,z:0,sx:2,sz:1,ystart:0}
function medusa:maze/wall/spawn_display {x:3,z:1,sx:2,sz:1,ystart:0}
function medusa:maze/wall/spawn_controller {mode:1}
execute as @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1,limit=1,sort=nearest] run function medusa:maze/wall/animate_open
execute as @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1] run tag @s remove md.maze.new_wall_controller
