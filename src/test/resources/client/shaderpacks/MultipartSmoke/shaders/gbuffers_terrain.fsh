#version 120
uniform sampler2D texture;
varying vec2 uv;
varying vec4 color;
void main() { gl_FragData[0] = texture2D(texture, uv) * color; }
