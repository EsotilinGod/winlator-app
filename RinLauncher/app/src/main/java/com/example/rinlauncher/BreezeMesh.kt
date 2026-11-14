package com.example.rinlauncher

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Brisa suave mediante una malla deformable (Canvas.drawBitmapMesh) aplicada SOLO a la capa
 * breeze_layer.png, que contiene únicamente las piezas que se mecen (lazo, mechón, pañuelo, correa).
 * El fondo se dibuja aparte, fijo, así que los circuitos no se deforman.
 *
 * Los vértices se desplazan dentro de zonas de pelo y ropa ("flaps"). Cada zona tiene un
 * ancla fija (raíz del lazo, nacimiento del mechón, nudo del pañuelo, hebilla de la correa) y el
 * desplazamiento crece hacia la punta, como en una viga en voladizo. Una onda viajera
 * (fase - k·distancia) hace que la punta vaya retrasada respecto de la raíz. La cara está
 * protegida por una máscara elíptica con peso 0.
 *
 * Coste: los pesos se calculan una sola vez; en cada fotograma solo se recorren los vértices
 * activos (unos cientos de los ~3300), así que la CPU apenas trabaja.
 */
class BreezeMesh(private val imgW: Int, private val imgH: Int) {

    companion object {
        // Recorte de breeze_layer.png dentro de la ilustración (ver tools/make_breeze_layer.py).
        const val LAYER_X = 180f
        const val LAYER_Y = 520f
        const val LAYER_W = 720f
        const val LAYER_H = 1280f
    }

    private class Flap(
        val cx: Float, val cy: Float, val rx: Float, val ry: Float, // elipse de influencia (normalizada)
        val ax: Float, val ay: Float, val length: Float,             // ancla (normalizada) y largo (px)
        dirX: Float, dirY: Float, val amp: Float, val phase: Float   // dirección, amplitud (px), fase
    ) {
        val dx: Float
        val dy: Float
        init {
            val n = sqrt(dirX * dirX + dirY * dirY)
            dx = dirX / n; dy = dirY / n
        }
        lateinit var index: IntArray      // vértices afectados
        lateinit var weight: FloatArray   // peso de cada uno
        lateinit var distNorm: FloatArray // distancia al ancla / largo
    }

    // Parámetros calibrados sobre rin_wallpaper.jpg (1220 × 2712) con un prototipo previo.
    private val flaps = listOf(
        Flap(.470f, .240f, .125f, .062f, .495f, .290f, 230f, 1.0f, 0.12f, 15f, 0.0f), // lazo
        Flap(.390f, .340f, .100f, .040f, .475f, .318f, 210f, 0.25f, 1.0f, 10f, 1.1f), // mechón izq.
        Flap(.310f, .465f, .190f, .068f, .470f, .410f, 450f, 0.20f, 1.0f, 22f, 2.0f), // pañuelo
        Flap(.620f, .590f, .100f, .078f, .600f, .530f, 290f, 1.0f, -0.08f, 9f, 3.1f)  // correa
    )
    private val faceCx = .505f; private val faceCy = .350f
    private val faceRx = .062f; private val faceRy = .036f

    val cols = 36
    val rows = 64
    private val base = FloatArray((cols + 1) * (rows + 1) * 2)
    val verts = FloatArray(base.size)

    init {
        var k = 0
        for (r in 0..rows) for (c in 0..cols) {
            // Coordenadas locales de la capa (lo que consume drawBitmapMesh).
            base[k++] = LAYER_W * c / cols
            base[k++] = LAYER_H * r / rows
        }
        base.copyInto(verts)

        for (f in flaps) {
            val idx = ArrayList<Int>(); val wts = ArrayList<Float>(); val dst = ArrayList<Float>()
            for (r in 1 until rows) for (c in 1 until cols) { // el borde de la capa nunca se mueve
                val v = r * (cols + 1) + c
                // Los pesos se evalúan en coordenadas de la ilustración completa.
                val px = base[v * 2] + LAYER_X; val py = base[v * 2 + 1] + LAYER_Y
                val nx = px / imgW; val ny = py / imgH
                val er = hypot((nx - f.cx) / f.rx, (ny - f.cy) / f.ry)
                val ell = 1f - smoothstep(0.75f, 1.15f, er)
                if (ell <= 0f) continue
                val face = smoothstep(1.0f, 1.6f, hypot((nx - faceCx) / faceRx, (ny - faceCy) / faceRy))
                val d = hypot(px - f.ax * imgW, py - f.ay * imgH) / f.length
                val w = ell * face * d.coerceIn(0f, 1f).pow(1.1f)
                if (w > 0.002f) { idx += v; wts += w; dst += d }
            }
            f.index = idx.toIntArray(); f.weight = wts.toFloatArray(); f.distNorm = dst.toFloatArray()
        }
    }

    /** Actualiza [verts] para el instante [t] (segundos). */
    fun update(t: Float) {
        base.copyInto(verts)
        val gust = 0.65f + 0.35f * sin(2f * PI.toFloat() * t / 11f) // ráfagas lentas
        val omega = 2f * PI.toFloat() / 3.4f
        for (f in flaps) {
            val a = f.amp * gust
            for (i in f.index.indices) {
                val k = omega * t + f.phase - 2.2f * f.distNorm[i]
                val s = 0.72f * sin(k) + 0.28f * sin(2.3f * k + 0.7f)
                val m = f.weight[i] * a * s
                val v = f.index[i] * 2
                verts[v] += m * f.dx
                verts[v + 1] += m * f.dy
            }
        }
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
