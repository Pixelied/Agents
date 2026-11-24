#version 330
in float selectedShader;
in vec3 aPos;
in vec2 aTex;
in vec4 aColor;
in float u_Inner;
in float u_Feather;
in vec2 u_CenterPos;
in float u_MiddleAngle;
in float u_SweepAngle;
in vec2 screen_dimensions;
in vec2 origin;
in vec4 u_InnerRect;
in vec3 u_Radius3;
in float u_Spread;
in vec4 u_Corners;
out float mode;
out vec3 pos;
out vec2 uv;
out vec4 color;
out float inner;
out float feather;
out vec2 center;
out float middle;
out float sweep;
out vec2 screen;
out vec2 org;
out vec4 bounds;
out vec3 radius;
out float spread;
out vec4 corners;
void main(){
 gl_Position=vec4(aPos.x/64.0-1.0,1.0-aPos.y/64.0,0.0,1.0);
 mode=selectedShader;pos=aPos;uv=aTex;color=aColor;inner=u_Inner;feather=u_Feather;center=u_CenterPos;middle=u_MiddleAngle;sweep=u_SweepAngle;screen=screen_dimensions;org=origin;bounds=u_InnerRect;radius=u_Radius3;spread=u_Spread;corners=u_Corners;
}
