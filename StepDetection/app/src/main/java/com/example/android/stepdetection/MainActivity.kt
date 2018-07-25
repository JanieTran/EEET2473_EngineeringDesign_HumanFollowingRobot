package com.example.android.stepdetection

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Constants
    val ABOVE: Int = 1
    val BELOW: Int = 0

    // Variables
    lateinit var sensorManager: SensorManager

    lateinit var rawData: LineGraphSeries<DataPoint>
    lateinit var lpData: LineGraphSeries<DataPoint>

    var streakPrevTime: Long = 0
    var streakStartTime: Long = 0
    var startTime: Long = 0

    var prev: FloatArray = FloatArray(3)

    var stepCount: Int = 0
    var CURRENT_STATE: Int = 0
    var PREVIOUS_STATE: Int = BELOW

    var rawPoints: Double = 0.0
    var sampleCount: Int = 0
    var SAMPLING_ACTIVE: Boolean = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Raw Data graph
        rawData = LineGraphSeries()
        rawData.title = "Raw Data"
        rawData.color = Color.RED

        gv_raw.viewport.isYAxisBoundsManual = true
        gv_raw.viewport.setMinY(-40.0)
        gv_raw.viewport.setMaxY(30.0)

        gv_raw.viewport.isXAxisBoundsManual = true
        gv_raw.viewport.setMinX(4.0)
        gv_raw.viewport.setMaxX(80.0)

        gv_raw.addSeries(rawData)

        // Low Pass Filter graph
        lpData = LineGraphSeries()
        lpData.title = "Smooth Data"
        lpData.color = Color.BLUE

        gv_lowPass.viewport.isYAxisBoundsManual = true
        gv_lowPass.viewport.setMinY(-30.0)
        gv_lowPass.viewport.setMaxY(30.0)

        gv_lowPass.viewport.isXAxisBoundsManual = true
        gv_lowPass.viewport.setMinX(4.0)
        gv_lowPass.viewport.setMaxX(80.0)

        gv_lowPass.addSeries(lpData)

        // Combined Plot
        gv_combined.viewport.isYAxisBoundsManual = true
        gv_combined.viewport.setMinY(-70.0)
        gv_combined.viewport.setMaxY(70.0)

        gv_combined.viewport.isXAxisBoundsManual = true
        gv_combined.viewport.setMinX(4.0)
        gv_combined.viewport.setMaxX(80.0)

        gv_combined.addSeries(rawData)
        gv_combined.addSeries(lpData)

        streakPrevTime = System.currentTimeMillis() - 500
    }

    override fun onResume() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL)

        super.onResume()
        startTime = System.currentTimeMillis()
    }

    private fun handleEvent(event: SensorEvent) {
        prev = lowPassFilter(event.values, prev)

        val raw = Accelerometer(event.values)
        val data = Accelerometer(prev)

        rawData.appendData(DataPoint(rawPoints++, raw.R), true, 1000)
        lpData.appendData(DataPoint(rawPoints, data.R), true, 1000)

        if (data.R > 10.5) {
            CURRENT_STATE = ABOVE

            if (PREVIOUS_STATE != CURRENT_STATE) {
                streakStartTime = System.currentTimeMillis()

                if (streakStartTime - streakPrevTime <= 250) {
                    streakPrevTime = System.currentTimeMillis()
                    return
                }

                streakPrevTime = streakStartTime
                Log.d("STATES", "$streakPrevTime $streakStartTime")
                stepCount++
            }

            PREVIOUS_STATE = CURRENT_STATE
        } else {
            CURRENT_STATE = BELOW
            PREVIOUS_STATE = CURRENT_STATE
        }

        tv_counter.text = "$stepCount"
    }

    private fun lowPassFilter(input: FloatArray, prev: FloatArray): FloatArray {
        val alpha = 0.1f

        for (i in 0 until input.size) {
            prev[i] += alpha * (input[i] - prev[i])
        }

        return prev
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                handleEvent(event)

                if (SAMPLING_ACTIVE) {
                    sampleCount++
                    val now: Long = System.currentTimeMillis()
                    if (now >= startTime + 5000) {
                        val samplingRate: Double = sampleCount / ((now - startTime) / 1000.0)
                        SAMPLING_ACTIVE = false
                        Toast.makeText(applicationContext,
                                "Sampling rate ${samplingRate}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

}
