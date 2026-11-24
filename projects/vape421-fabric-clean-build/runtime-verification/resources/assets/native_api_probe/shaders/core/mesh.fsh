#version 330
in float mode;
in vec3 pos;
in vec2 uv;
in vec4 color;
in float inner;
in float feather;
in vec2 center;
in float middle;
in float sweep;
in vec2 screen;
in vec2 org;
in vec4 bounds;
in vec3 radius;
in float spread;
in vec4 corners;
uniform sampler2D imgTexture;
out vec4 fragColor;
void main(){
 int m=int(mode+0.2);
 if(m==2){
  float a=1.0-smoothstep(radius.x-feather,radius.x,length(pos.xy-center));
  fragColor=vec4(color.rgb,color.a*a);
 }else if(m==5){
  vec2 dis=max(bounds.xy-pos.xy,pos.xy-bounds.zw);
  bool corner=(pos.x<=bounds.x&&pos.y<=bounds.y&&corners.x>0.5)||(pos.x>=bounds.z&&pos.y<=bounds.y&&corners.y>0.5)||(pos.x>=bounds.z&&pos.y>=bounds.w&&corners.z>0.5)||(pos.x<=bounds.x&&pos.y>=bounds.w&&corners.w>0.5);
  float a=corner?1.0-smoothstep(0.0,spread,length(max(vec2(0.0),dis))-radius.x):1.0;
  fragColor=vec4(color.rgb,color.a*a);
 }else if(m==11){
  float a=texture(imgTexture,uv).r;
  a=max(0.0,a+0.25*(1.0-a));
  a=mix(a,0.0,1.0-smoothstep(0.2,0.8,a));
  fragColor=vec4(color.rgb,color.a*a);
 }else if(m==99){
  // Diagnostic sentinel checks the remaining original attributes, not a production shader mode.
  fragColor=vec4(inner+feather+center.x+center.y+middle+sweep,
    screen.x+screen.y+org.x+org.y+dot(bounds,vec4(1.0)),
    dot(radius,vec3(1.0))+spread+dot(corners,vec4(1.0))+pos.z+color.r,1.0);
 }else if(m==9){fragColor=color;}
 else {fragColor=vec4(1.0,0.0,1.0,1.0);}
}
