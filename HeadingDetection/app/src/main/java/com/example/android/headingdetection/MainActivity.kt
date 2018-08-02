package com.example.android.headingdetection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var magnetometer: Sensor

    private var lastAcc: FloatArray = FloatArray(3)
    private var lastMag: FloatArray = FloatArray(3)
    private var lastAccSet: Boolean = false
    private var lastMagSet: Boolean = false

    private var rotation: FloatArray = FloatArray(9)
    private var orientation: FloatArray = FloatArray(3)
    private var currentDegree: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    override fun onResume() {
        super.onResume()

        sensorManager.registerListener(this, accelerometer, 10000)
        sensorManager.registerListener(this, magnetometer, 10000)
    }

    override fun onPause() {
        super.onPause()

        sensorManager.unregisterListener(this, accelerometer)
        sensorManager.unregisterListener(this, magnetometer)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor == accelerometer) {
                System.arraycopy(event.values, 0, lastAcc, 0, event.values.size)
                lastAccSet = true
            } else if (event.sensor == magnetometer) {
                System.arraycopy(event.values, 0, lastMag, 0, event.values.size)
                lastMagSet = true
            }

            if (lastAccSet && lastMagSet) {
                SensorManager.getRotationMatrix(rotation, null, lastAcc, lastMag)
                SensorManager.getOrientation(rotation, orientation)

                val radian = orientation[0]
                val degree = ((Math.toDegrees(radian.toDouble()) + 360) % 360).toFloat()

                val rotateAnimation = RotateAnimation(
                        currentDegree, - degree,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f)
                rotateAnimation.duration = 250
                rotateAnimation.fillAfter = true

                iv_pointer.startAnimation(rotateAnimation)
                currentDegree = - degree
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
}
