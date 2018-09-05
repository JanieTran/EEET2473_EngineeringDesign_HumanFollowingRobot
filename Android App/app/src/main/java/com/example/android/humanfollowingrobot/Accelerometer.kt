package com.example.android.humanfollowingrobot

class Accelerometer(event: FloatArray) {
    var X: Float = event[0]
    var Y: Float = event[1]
    var Z: Float = event[2]
    val R: Double = Math.sqrt((X*X + Y*Y + Z*Z).toDouble())

    fun linearAcceleration(): Float {
        return Math.sqrt((X*X + Y*Y).toDouble()).toFloat()
    }
}