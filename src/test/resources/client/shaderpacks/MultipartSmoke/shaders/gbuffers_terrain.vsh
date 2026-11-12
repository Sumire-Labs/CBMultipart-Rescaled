#version 120
varying vec2 uv;
varying vec4 color;
void main() {
    gl_Position = ftransform();
    uv = gl_MultiTexCoord0.xy;
    color = gl_Color;
}
