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

out float f_SelectedShader;
out vec3 f_Position;
out vec2 f_TexCoords;
out vec4 f_VertexColor;
out float f_Inner;
out float f_Feather;
out vec2 f_CenterPos;
out float f_MiddleAngle;
out float f_SweepAngle;
out vec2 f_ScreenDimensions;
out vec2 f_Origin;
out vec4 f_InnerRect;
out vec3 f_Radius3;
out float f_Spread;
out vec4 f_Corners;


void main() {
    // Diagnostic atlas projection only; original shader varyings are unchanged.
    vec2 pixel = aPos.xy + vec2(mod(selectedShader, 7.0), floor(selectedShader / 7.0)) * 64.0;
    gl_Position = vec4(pixel.x / 224.0 - 1.0, 1.0 - pixel.y / 96.0, 0.0, 1.0);

    f_SelectedShader = selectedShader;
    f_Position = aPos;
    f_TexCoords = aTex;
    f_VertexColor = aColor;
    f_Inner = u_Inner;
    f_Feather = u_Feather;
    f_CenterPos = u_CenterPos;
    f_MiddleAngle = u_MiddleAngle;
    f_SweepAngle = u_SweepAngle;
    f_ScreenDimensions = screen_dimensions;
    f_Origin = origin;
    f_InnerRect = u_InnerRect;
    f_Radius3 = u_Radius3;
    f_Spread = u_Spread;
    f_Corners = u_Corners;
}
