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

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Constants
    private val ABOVE: Int = 1
    private val BELOW: Int = 0
    private val MAX_Y: Double = 20.0
    private val MAX_Y_FILTER: Double = 10.0
    private val MIN_X: Double = 4.0
    private val MAX_X: Double = 50.0

    // Variables
    private lateinit var sensorManager: SensorManager

    private lateinit var rawData: LineGraphSeries<DataPoint>
    private lateinit var hpPlot: LineGraphSeries<DataPoint>
    private lateinit var lpPlot: LineGraphSeries<DataPoint>

    private var streakPrevTime: Long = 0
    private var streakStartTime: Long = 0
    private var startTime: Long = 0

    private var prev: FloatArray = FloatArray(3)

    private var stepCount: Int = 0
    private var CURRENT_STATE: Int = 0
    private var PREVIOUS_STATE: Int = BELOW

    private var rawPoints: Double = 0.0
    private var sampleCount: Int = 0
    private var SAMPLING_ACTIVE: Boolean = true

    // High pass filter
    private var accHPavg: FloatArray = FloatArray(3)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Raw Data graph
        rawData = LineGraphSeries()
        rawData.title = "Raw Data"
        rawData.color = Color.RED

        gv_raw.viewport.isYAxisBoundsManual = true
        gv_raw.viewport.setMinY(0.0)
        gv_raw.viewport.setMaxY(MAX_Y)

        gv_raw.viewport.isXAxisBoundsManual = true
        gv_raw.viewport.setMinX(MIN_X)
        gv_raw.viewport.setMaxX(MAX_X)

        gv_raw.addSeries(rawData)

        // High Pass Filter graph
        hpPlot = LineGraphSeries()
        hpPlot.title = "Smooth Data"
        hpPlot.color = Color.BLUE

        gv_highPass.viewport.isYAxisBoundsManual = true
        gv_highPass.viewport.setMinY(0.0)
        gv_highPass.viewport.setMaxY(MAX_Y_FILTER)

        gv_highPass.viewport.isXAxisBoundsManual = true
        gv_highPass.viewport.setMinX(MIN_X)
        gv_highPass.viewport.setMaxX(MAX_X)

        gv_highPass.addSeries(hpPlot)

        // Low Pass Filter graph
        lpPlot = LineGraphSeries()
        lpPlot.title = "Smooth Data"
        lpPlot.color = Color.BLUE

        gv_lowPass.viewport.isYAxisBoundsManual = true
        gv_lowPass.viewport.setMinY(0.0)
        gv_lowPass.viewport.setMaxY(MAX_Y_FILTER)

        gv_lowPass.viewport.isXAxisBoundsManual = true
        gv_lowPass.viewport.setMinX(MIN_X)
        gv_lowPass.viewport.setMaxX(MAX_X)

        gv_lowPass.addSeries(lpPlot)

        streakPrevTime = System.currentTimeMillis() - 500
    }

    override fun onResume() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                50000)

        super.onResume()
        startTime = System.currentTimeMillis()
    }

    private fun handleEvent(event: SensorEvent) {
        val hpFiltered = highPassFilter(event.values)
//        prev = lowPassFilter(event.values, prev)
        prev = lowPassFilter(hpFiltered, prev)

        val raw = Accelerometer(event.values)
        val hpData = Accelerometer(hpFiltered)
        val lpData = Accelerometer(prev)

        rawData.appendData(DataPoint(rawPoints++, raw.R), true, 100)
        hpPlot.appendData(DataPoint(rawPoints, hpData.R), true, 100)
        lpPlot.appendData(DataPoint(rawPoints, lpData.R), true, 100)

        if (lpData.R > 1.0 && lpData.R < 2.0) {
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
                                "Sampling rate $samplingRate", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

}
