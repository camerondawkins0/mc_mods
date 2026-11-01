#version 330

// "Frontier Vision": a light colour grade for the Frontier — a soft glow around bright light,
// a little extra colour and contrast, a per-region tint and a gentle vignette.

uniform sampler2D InSampler;
// 8x1 texture FrontierVision rewrites every tick: one grade value per texel, packed into 16 bits
// (red = high byte, green = low byte) over the range [-1, 3].
uniform sampler2D ParamsSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 ParamsSize;
};

in vec2 texCoord;

out vec4 fragColor;

float param(int i) {
    vec4 p = texelFetch(ParamsSampler, ivec2(i, 0), 0);
    return (p.r * 65280.0 + p.g * 255.0) / 65535.0 * 4.0 - 1.0;
}

vec3 bright(vec2 uv) {
    vec3 c = texture(InSampler, uv).rgb;
    return max(c - 0.72, 0.0);
}

void main() {
    float Saturation = param(0);
    float Contrast = param(1);
    float Warmth = param(2);
    vec3 Tint = vec3(param(3), param(4), param(5));
    float Bloom = param(6);
    float Vignette = param(7);

    vec2 oneTexel = 1.0 / InSize;
    vec3 color = texture(InSampler, texCoord).rgb;

    // Soft glow: bright texels bleed into their neighbours on two rings.
    vec3 glow = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;
        vec2 dir = vec2(cos(a), sin(a));
        glow += bright(texCoord + dir * oneTexel * 3.0) * 0.6;
        glow += bright(texCoord + dir * oneTexel * 7.0) * 0.4;
    }
    color += glow / 8.0 * Bloom * 2.0;

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, Saturation);
    color = (color - 0.5) * Contrast + 0.5;
    color *= vec3(1.0 + Warmth, 1.0 + Warmth * 0.3, 1.0 - Warmth) * Tint;

    vec2 d = texCoord - 0.5;
    color *= 1.0 - Vignette * smoothstep(0.18, 0.75, dot(d, d) * 2.0);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
