package com.example.android.humanfollowingrobot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import app.akexorcist.bluetotohspp.library.BluetoothSPP
import app.akexorcist.bluetotohspp.library.BluetoothState
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.Toast
import app.akexorcist.bluetotohspp.library.DeviceList

class MainActivity : AppCompatActivity(), SensorEventListener {
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // CONSTANTS
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private val ABOVE: Int = 1
    private val BELOW: Int = 0

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // GLOBAL VARIABLES
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    // Bluetooth
    //-------------------------------------------------------------------
    private lateinit var bluetooth: BluetoothSPP

    // Sensors
    //-------------------------------------------------------------------
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var magnetometer: Sensor

    // Accelerometer
    //-------------------------------------------------------------------

    private var streakPrevTime: Long = 0
    private var streakStartTime: Long = 0
    private var startTime: Long = 0

    private var prev: FloatArray = FloatArray(3)

    private var stepCount: Int = 0
    private var currentState: Int = 0
    private var previousState: Int = BELOW

    private var sampleCount: Int = 0
    private var samplingActive: Boolean = true

    private var accHPavg: FloatArray = FloatArray(3)

    // Magnetometer
    //-------------------------------------------------------------------
    private var lastAcc: FloatArray = FloatArray(3)
    private var lastMag: FloatArray = FloatArray(3)

    private var rotation: FloatArray = FloatArray(9)
    private var orientation: FloatArray = FloatArray(3)
    private var currentDegree: Float = 0f

    // Velocity
    //-------------------------------------------------------------------
    private var gravity: FloatArray = FloatArray(3)
    private var t0: Long = 0
    private var v0: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // BLUETOOTH
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        bluetooth = BluetoothSPP(this)

        // Check if Bluetooth available
        if (!bluetooth.isBluetoothAvailable) {
            Toast.makeText(applicationContext, "Bluetooth is not available", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Listener for Bluetooth connection status
        bluetooth.setBluetoothConnectionListener(object : BluetoothSPP.BluetoothConnectionListener {
            // When two devices are successfully connected
            override fun onDeviceConnected(name: String, address: String) {
                btn_connect.text = "Connected to $name"
            }

            // When the current connection is terminated
            override fun onDeviceDisconnected() {
                btn_connect.text = "Connection lost"
            }

            // When cannot connect to device
            override fun onDeviceConnectionFailed() {
                btn_connect.text = "Unable to connect"
            }
        })

        // Scenarios for button to connect Bluetooth
        btn_connect.setOnClickListener {
            // When already connected to a device, disconnect it
            if (bluetooth.serviceState == BluetoothState.STATE_CONNECTED)
                bluetooth.disconnect()
            // When not connected, show list of available devices to choose
            else {
                val intent = Intent(applicationContext, DeviceList::class.java)
                startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE)
            }
            bluetooth.startDiscovery()
        }

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // BUTTON LISTENERS
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        // Button to command the robot to start following
        btn_start.setOnClickListener {
            bluetooth.send("Start", true)

            sensorManager.registerListener(this, accelerometer, 50000)
            sensorManager.registerListener(this, magnetometer, 10000)

            // Initialise parameters for velocity calculation
            t0 = System.currentTimeMillis() / 1000
        }

        // Button to command the robot to stop following
        btn_stop.setOnClickListener {
            bluetooth.send("Stop", true)

            sensorManager.unregisterListener(this, accelerometer)
            sensorManager.unregisterListener(this, magnetometer)
        }

        // Button to command the robot to turn left
        btn_left.setOnClickListener {
            bluetooth.send("L", true)
        }

        // Button to command the robot to turn left
        btn_right.setOnClickListener {
            bluetooth.send("R", true)
        }

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // STEP DETECTION
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        streakPrevTime = System.currentTimeMillis() - 500
    }

    override fun onStart() {
        super.onStart()

        if (!bluetooth.isBluetoothEnabled)
            bluetooth.enable()
        else {
            if (!bluetooth.isServiceAvailable) {
                bluetooth.setupService()
                bluetooth.startService(BluetoothState.DEVICE_OTHER)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        bluetooth.stopService()
    }

    override fun onStop() {
        super.onStop()

        sensorManager.unregisterListener(this, accelerometer)
        sensorManager.unregisterListener(this, magnetometer)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if (resultCode == Activity.RESULT_OK)
                bluetooth.connect(data)
        }
        else if (requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK)
                bluetooth.setupService()
            else {
                println("Bluetooth was not enabled")
                finish()
            }
        }
    }

    //==================================================================================
    // SENSOR EVENT LISTENER
    //==================================================================================

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            // Accelerometer
            //-----------------------------------------
            if (event.sensor == accelerometer) {
                detectStep(event)

                if (samplingActive) {
                    sampleCount++
                    val now: Long = System.currentTimeMillis()

                    if (now >= startTime + 5000) {
                        samplingActive = false
                    }
                }

                System.arraycopy(event.values, 0, lastAcc,
                        0, event.values.size)
            }

            // Magnetometer
            //-----------------------------------------
            else if (event.sensor == magnetometer) {
                System.arraycopy(event.values, 0, lastMag,
                        0 , event.values.size)
            }
        }
    }

    //==================================================================================
    // HELPER METHODS
    //==================================================================================

    private fun detectStep(event: SensorEvent) {
        val hpFiltered = highPassFilter(event.values)
        prev = lowPassFilter(hpFiltered, prev)

        val lpData = Accelerometer(prev)

        if (lpData.R > 1.0 && lpData.R < 2.0) {
            currentState = ABOVE

            if (previousState != currentState) {
                streakStartTime = System.currentTimeMillis()

                if (streakStartTime - streakPrevTime <= 250) {
                    streakPrevTime = System.currentTimeMillis()
                    return
                }

                streakPrevTime = streakStartTime
                stepCount++

                updateDirection()
                calculateVelocity(event)
            }

            previousState = currentState
        } else {
            currentState = BELOW
            previousState = currentState
        }

        tv_stepCount.text = "$stepCount"
    }

    private fun highPassFilter(input: FloatArray): FloatArray {
        val alpha = 0.8f
        val accHPfiltered = FloatArray(3)

        for (i in 0 until input.size) {
            accHPavg[i] = input[i] * (1 - alpha) + accHPavg[i] * alpha
            accHPfiltered[i] = input[i] - accHPavg[i]
        }

        return accHPfiltered
    }

    private fun lowPassFilter(input: FloatArray, prev: FloatArray): FloatArray {
        val alpha = 0.8f

        for (i in 0 until input.size) {
            prev[i] += alpha * (input[i] - prev[i])
        }

        return prev
    }

    private fun updateDirection() {
        SensorManager.getRotationMatrix(rotation, null, lastAcc, lastMag)
        SensorManager.getOrientation(rotation, orientation)

        val radian = orientation[0]
        val degree = ((Math.toDegrees(radian.toDouble()) + 360) % 360).toFloat()

        getDirection(degree)
        getHeading(degree)

        currentDegree = degree
    }

    private fun getDirection(degree: Float) {
        var deltaDegree = degree - currentDegree
        var direction = "Straight"

        if (deltaDegree > 180) {
            deltaDegree = - (360 - degree + currentDegree)
        } else if (deltaDegree < -180) {
            deltaDegree = 360 - currentDegree + degree
        }

        if (deltaDegree > 15) {
            direction = "Right"
        } else if (deltaDegree < -15) {
            direction = "Left"
        }

        tv_currentDegree.text = "Current: $currentDegree"
        tv_newDegree.text = "New: $degree"
        tv_deltaDegree.text = "Delta: $deltaDegree"
        tv_direction.text = direction
    }

    private fun getHeading(degree: Float) {
        val heading = when (degree) {
            in 0..22 -> "N"
            in 23..67 -> "NE"
            in 68..112 -> "E"
            in 113..157 -> "SE"
            in 158..202 -> "S"
            in 203..247 -> "SW"
            in 248..292 -> "W"
            in 293..337 -> "NW"
            in 338..360 -> "N"
            else -> "--"
        }

        tv_heading.text = heading
    }

    private fun calculateVelocity(event: SensorEvent) {
        val alpha = 0.8f
        val acceleration = Accelerometer(FloatArray(3))
        val values = event.values
        val t = event.timestamp / 1000000000

        for (i in 0 until values.size) {
            gravity[i] = alpha * gravity[i] + (1 - alpha) * values[i]
        }

        acceleration.X = values[0] - gravity[0]
        acceleration.Y = values[1] - gravity[1]

        val linearAcc = acceleration.linearAcceleration()
        val dt = t - t0
        val v: Float  = linearAcc * dt + v0

        Log.d("Velocity", "a = $linearAcc")
        Log.d("Velocity", "dt = $dt")
        Log.d("Velocity", "v = $v")

        t0 = t
        v0 = v

        tv_velocity.text = "$v m/s"


        Log.d("Velocity", "v0 = $v0")
    }
}
