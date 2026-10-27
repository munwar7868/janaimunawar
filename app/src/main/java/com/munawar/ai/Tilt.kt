package com.munawar.ai

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View

/**
 * Phone-tilt parallax: layers move/rotate a little when you tilt the phone, which makes the Home screen feel 3D.
 * Each layer has a depth (bigger = moves more). Cheap: accelerometer only, smoothed, stops when the screen is hidden.
 */
class Tilt(private val ctx: Context) : SensorEventListener {
    private class Layer(val v: View, val depth: Float)

    private val layers = ArrayList<Layer>()
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var running = false
    var strength = 1f
    private var tx = 0f
    private var ty = 0f

    fun add(v: View, depth: Float): Tilt { v.enable3d(); layers.add(Layer(v, depth)); return this }

    fun start() {
        if (running) return
        val s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        running = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        sm.unregisterListener(this)
        running = false
        layers.forEach { it.v.animate().rotationX(0f).rotationY(0f).translationX(0f).translationY(0f).setDuration(200).start() }
    }

    override fun onSensorChanged(e: SensorEvent) {
        // x: left/right tilt, y: forward/back tilt (portrait). Normalised to about -1..1 and smoothed.
        val nx = (-e.values[0] / 6f).coerceIn(-1f, 1f)
        val ny = ((e.values[1] - 6.5f) / 6f).coerceIn(-1f, 1f)
        tx += (nx - tx) * 0.12f
        ty += (ny - ty) * 0.12f
        val d = ctx.resources.displayMetrics.density
        for (l in layers) {
            l.v.rotationY = tx * 9f * l.depth * strength
            l.v.rotationX = ty * 9f * l.depth * strength
            l.v.translationX = tx * 10f * d * l.depth * strength
            l.v.translationY = ty * 6f * d * l.depth * strength
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
