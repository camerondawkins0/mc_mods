#version 150

// "Frontier Vision": a light colour grade for the Frontier — a soft glow around bright light,
// a little extra colour and contrast, a per-region tint and a gentle vignette.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform float Saturation;
uniform float Contrast;
uniform float Warmth;
uniform float TintR;
uniform float TintG;
uniform float TintB;
uniform float Bloom;
uniform float Vignette;

out vec4 fragColor;

vec3 bright(vec2 uv) {
    vec3 c = texture(DiffuseSampler, uv).rgb;
    return max(c - 0.72, 0.0);
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;

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
    color *= vec3(1.0 + Warmth, 1.0 + Warmth * 0.3, 1.0 - Warmth) * vec3(TintR, TintG, TintB);

    vec2 d = texCoord - 0.5;
    color *= 1.0 - Vignette * smoothstep(0.18, 0.75, dot(d, d) * 2.0);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
