#version 330 compatibility

varying vec2 vUv;

uniform sampler2D uScene;
uniform vec4 uCon0;
uniform vec4 uCon1;
uniform vec4 uCon2;
uniform vec4 uCon3;
uniform float uSharpness;

vec3 FsrEasuSampleH(vec2 p) {
    return texture2D(uScene, p).rgb;
}

float APrxLoRcpF1(float a) {
    return uintBitsToFloat(uint(0x7ef07ebb) - floatBitsToUint(a));
}

float APrxLoRsqF1(float a) {
    return uintBitsToFloat(uint(0x5f347d74) - (floatBitsToUint(a) >> uint(1)));
}

float AMin3F1(float x, float y, float z) {
    return min(x, min(y, z));
}

float AMax3F1(float x, float y, float z) {
    return max(x, max(y, z));
}

float ASatF1(float v) {
    return clamp(v, 0.0, 1.0);
}

void main() {
    vec2 ip = floor(gl_FragCoord.xy);
    vec2 pp = ip * uCon0.xy + uCon0.zw;
    vec2 tc = (pp + vec2(0.5)) * uCon1.xy;

    vec4 sCenter = texture2D(uScene, tc);
    if (sCenter.a < 0.999) {
        gl_FragColor = sCenter;
        return;
    }

    vec3 sC = sCenter.rgb;
    vec3 sA = FsrEasuSampleH(tc - vec2(0.0, uCon1.y));
    vec3 sB = FsrEasuSampleH(tc - vec2(uCon1.x, 0.0));
    vec3 sD = FsrEasuSampleH(tc + vec2(uCon1.x, 0.0));
    vec3 sE = FsrEasuSampleH(tc + vec2(0.0, uCon1.y));

    float mn4R = min(min(sA.r, sB.r), min(sD.r, sE.r));
    float mn4G = min(min(sA.g, sB.g), min(sD.g, sE.g));
    float mn4B = min(min(sA.b, sB.b), min(sD.b, sE.b));
    float mx4R = max(max(sA.r, sB.r), max(sD.r, sE.r));
    float mx4G = max(max(sA.g, sB.g), max(sD.g, sE.g));
    float mx4B = max(max(sA.b, sB.b), max(sD.b, sE.b));

    vec2 peakC = vec2(1.0, -4.0);
    float hitMinR = mn4R * (1.0 / (4.0 * mx4R));
    float hitMinG = mn4G * (1.0 / (4.0 * mx4G));
    float hitMinB = mn4B * (1.0 / (4.0 * mx4B));
    float hitMaxR = (peakC.x - mx4R) * (1.0 / (4.0 * mn4R + peakC.y));
    float hitMaxG = (peakC.x - mx4G) * (1.0 / (4.0 * mn4G + peakC.y));
    float hitMaxB = (peakC.x - mx4B) * (1.0 / (4.0 * mn4B + peakC.y));
    float lobeR = max(-hitMinR, hitMaxR);
    float lobeG = max(-hitMinG, hitMaxG);
    float lobeB = max(-hitMinB, hitMaxB);
    float lobe = max(-(0.25 - (1.0 / 16.0)), min(max(lobeR, max(lobeG, lobeB)), 0.0));
    lobe *= exp2(-clamp(uSharpness, 0.0, 2.0));

    float bL = sA.r * 0.5 + sA.g;
    float dL = sB.r * 0.5 + sB.g;
    float eL = sC.r * 0.5 + sC.g;
    float fL = sD.r * 0.5 + sD.g;
    float hL = sE.r * 0.5 + sE.g;

    float nz = 0.25 * bL + 0.25 * dL + 0.25 * fL + 0.25 * hL - eL;
    nz = clamp(abs(nz) * APrxLoRcpF1(AMax3F1(AMax3F1(bL, dL, eL), fL, hL) - AMin3F1(AMin3F1(bL, dL, eL), fL, hL)), 0.0, 1.0);
    nz = -0.5 * nz + 1.0;
    lobe *= nz;

    float rcpL = APrxLoRcpF1(4.0 * lobe + 1.0);
    vec3 contrast = (lobe * sA + lobe * sB + lobe * sD + lobe * sE) * rcpL;
    gl_FragColor = vec4(clamp(contrast + sC * rcpL, 0.0, 1.0), 1.0);
}
