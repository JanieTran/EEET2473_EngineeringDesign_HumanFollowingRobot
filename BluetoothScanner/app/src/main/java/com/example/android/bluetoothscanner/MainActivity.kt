package com.example.android.bluetoothscanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import com.example.android.bluetoothscanner.R.id.btn_scan
import com.example.android.bluetoothscanner.R.id.lv_devices
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {

    val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    lateinit var arrayAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        lv_devices.adapter = arrayAdapter

        btn_scan.setOnClickListener {
            arrayAdapter.clear()
            bluetoothAdapter.startDiscovery()
        }

        registerReceiver(actionFoundReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(actionFoundReceiver)
    }

    private val actionFoundReceiver: BroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String = intent!!.action

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                val device: String? = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                val rssi: Short? = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                arrayAdapter.add("Name: $device\nRSSI: $rssi dBm")
                arrayAdapter.notifyDataSetChanged()
            }

//            val task: TimerTask = object: TimerTask() {
//                override fun run() {
//                    runOnUiThread(object: TimerTask() {
//                        override fun run() {
////                            arrayAdapter.clear()
//                            bluetoothAdapter.startDiscovery()
//                        }
//                    })
//                }
//            }
//
//            val timer = Timer()
//            timer.schedule(task, 1000)
        }
    }
}

