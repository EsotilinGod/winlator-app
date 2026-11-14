package com.example.rinlauncher

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build

/**
 * Corriente de luz periódica que recorre los circuitos siguiendo sus trazos (Android 13+, AGSL).
 *
 * circuit_flow.png guarda, para cada píxel de circuito, la distancia medida A LO LARGO del trazo
 * desde la silueta de Rin (R = byte alto, G = byte bajo, B = válido). Cada [PERIOD] segundos un frente
 * de luz avanza por esa distancia: cabeza brillante y casi blanca, seguida de una estela dorada.
 * Fuera del pulso queda el brillo tenue de base, que respira lentamente.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
class CircuitCurrent(glow: Bitmap, flow: Bitmap) {

    companion object {
        const val PERIOD = 6.0f    // s entre pulsos
        const val SPEED = 110f     // px (capa a media resolución) por segundo ≈ 220 px/s en la imagen

        private const val AGSL = """
            uniform shader glow;
            uniform shader flow;
            uniform float t;
            uniform float period;
            uniform float speed;
            uniform float baseA;

            half4 main(float2 p) {
                half4 g = glow.eval(p);
                half4 f = flow.eval(p);
                float k = baseA;
                float head = 0.0;
                if (f.b > 0.5) {
                    float d = floor(float(f.r) * 255.0 + 0.5) * 256.0 + floor(float(f.g) * 255.0 + 0.5);
                    float x = mod(t, period) * speed - 30.0 - d;   // > 0: detrás de la cabeza
                    head = exp(-(x * x) / 400.0);
                    float tail = x > 0.0 ? 0.7 * exp(-x / 80.0) : 0.0;
                    k += 2.2 * max(head, tail);
                }
                // El alfa puede saturar a 1: el halo se vuelve un resplandor dorado intenso.
                float a = min(float(g.a) * k, 1.0);
                // Cabeza del pulso: dorado más cálido y luminoso (no blanco, para que destaque sobre el fondo claro).
                half3 col = mix(half3(1.0, 0.77, 0.25), half3(1.0, 0.86, 0.35), half(head));
                return half4(col * half(a), half(a));
            }
        """
    }

    private val shader = RuntimeShader(AGSL)
    val paint = Paint()

    init {
        shader.setInputShader("glow", BitmapShader(glow, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        // La distancia está codificada en bytes: hay que leerla sin interpolar.
        val flowShader = BitmapShader(flow, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_NEAREST
        }
        shader.setInputShader("flow", flowShader)
        shader.setFloatUniform("period", PERIOD)
        shader.setFloatUniform("speed", SPEED)
        paint.shader = shader
    }

    fun update(t: Float, baseAlpha: Float) {
        shader.setFloatUniform("t", t % (PERIOD * 600f))
        shader.setFloatUniform("baseA", baseAlpha)
        paint.shader = shader
    }
}
