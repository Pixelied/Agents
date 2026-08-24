function medusa:maze/wall/spawn_display {x:3,z:-1,sx:2,sz:1,ystart:8}
function medusa:maze/wall/spawn_display {x:3,z:0,sx:2,sz:1,ystart:8}
function medusa:maze/wall/spawn_display {x:3,z:1,sx:2,sz:1,ystart:8}
function medusa:maze/wall/spawn_controller {mode:2}
execute as @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1] run tag @s remove md.maze.new_wall_controller
