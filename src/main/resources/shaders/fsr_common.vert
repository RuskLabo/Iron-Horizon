#version 330 compatibility

varying vec2 vUv;

void main() {
    vUv = gl_MultiTexCoord0.xy;
    gl_Position = gl_Vertex;
}
