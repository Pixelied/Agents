function medusa:maze/wall/spawn_display {x:-1,z:3,sx:1,sz:2,ystart:8}
function medusa:maze/wall/spawn_display {x:0,z:3,sx:1,sz:2,ystart:8}
function medusa:maze/wall/spawn_display {x:1,z:3,sx:1,sz:2,ystart:8}
function medusa:maze/wall/spawn_controller {mode:2}
execute as @e[type=minecraft:marker,tag=md.maze.new_wall_controller,distance=..1] run tag @s remove md.maze.new_wall_controller
