package com.example.android.stepdetection

class Accelerometer(event: FloatArray) {

    val X: Float = event[0]
    val Y: Float = event[1]
    val Z: Float = event[2]
    val R: Double = Math.sqrt((X*X + Y*Y + Z*Z).toDouble())

    fun toNumber(): Number {
        val number: Number = R
        return number
    }

}