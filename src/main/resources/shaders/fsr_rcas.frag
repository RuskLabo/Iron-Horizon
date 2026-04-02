#version 330 compatibility

// Copyright (c) 2021-2022 Advanced Micro Devices, Inc. All rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

varying vec2 vUv;

uniform sampler2D uScene;
uniform float uSharpnessScale;

float APrxMedRcpF1(float a) {
    float b = uintBitsToFloat(uint(0x7ef19fff) - floatBitsToUint(a));
    return b * (-b * a + 2.0);
}

float AMax3F1(float x, float y, float z) {
    return max(x, max(y, z));
}

float AMin3F1(float x, float y, float z) {
    return min(x, min(y, z));
}

float luma(vec3 c) {
    return c.r * 0.5 + c.g;
}

void main() {
    vec2 uv = vUv;
    vec2 texel = vec2(1.0) / vec2(textureSize(uScene, 0));

    vec3 b = texture2D(uScene, uv + texel * vec2(0.0, -1.0)).rgb;
    vec3 d = texture2D(uScene, uv + texel * vec2(-1.0, 0.0)).rgb;
    vec4 ee = texture2D(uScene, uv);
    vec3 e = ee.rgb;
    vec3 f = texture2D(uScene, uv + texel * vec2(1.0, 0.0)).rgb;
    vec3 h = texture2D(uScene, uv + texel * vec2(0.0, 1.0)).rgb;

    float bL = luma(b);
    float dL = luma(d);
    float eL = luma(e);
    float fL = luma(f);
    float hL = luma(h);

    float nz = 0.25 * bL + 0.25 * dL + 0.25 * fL + 0.25 * hL - eL;
    nz = clamp(abs(nz) * APrxMedRcpF1(AMax3F1(AMax3F1(bL, dL, eL), fL, hL) - AMin3F1(AMin3F1(bL, dL, eL), fL, hL)), 0.0, 1.0);
    nz = -0.5 * nz + 1.0;

    float mn4R = min(AMin3F1(b.r, d.r, f.r), h.r);
    float mn4G = min(AMin3F1(b.g, d.g, f.g), h.g);
    float mn4B = min(AMin3F1(b.b, d.b, f.b), h.b);
    float mx4R = max(AMax3F1(b.r, d.r, f.r), h.r);
    float mx4G = max(AMax3F1(b.g, d.g, f.g), h.g);
    float mx4B = max(AMax3F1(b.b, d.b, f.b), h.b);

    vec2 peakC = vec2(1.0, -4.0);
    float hitMinR = min(mn4R, e.r) * (1.0 / (4.0 * mx4R));
    float hitMinG = min(mn4G, e.g) * (1.0 / (4.0 * mx4G));
    float hitMinB = min(mn4B, e.b) * (1.0 / (4.0 * mx4B));
    float hitMaxR = (peakC.x - max(mx4R, e.r)) * (1.0 / (4.0 * mn4R + peakC.y));
    float hitMaxG = (peakC.x - max(mx4G, e.g)) * (1.0 / (4.0 * mn4G + peakC.y));
    float hitMaxB = (peakC.x - max(mx4B, e.b)) * (1.0 / (4.0 * mn4B + peakC.y));
    float lobeR = max(-hitMinR, hitMaxR);
    float lobeG = max(-hitMinG, hitMaxG);
    float lobeB = max(-hitMinB, hitMaxB);
    float lobe = max(-(0.25 - (1.0 / 16.0)), min(AMax3F1(lobeR, lobeG, lobeB), 0.0)) * uSharpnessScale;
    lobe *= nz;

    float rcpL = APrxMedRcpF1(4.0 * lobe + 1.0);
    vec3 pix = (lobe * b + lobe * d + lobe * h + lobe * f + e) * rcpL;
    gl_FragColor = vec4(clamp(pix, 0.0, 1.0), ee.a);
}
