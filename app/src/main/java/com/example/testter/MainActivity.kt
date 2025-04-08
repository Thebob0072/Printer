package com.example.testter

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.testter.databinding.ActivityMainBinding
import com.example.testter.print.PrinterHelper
import net.posprinter.POSConnect
import net.posprinter.TSCPrinter
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var connectedDevice: BluetoothDevice? = null
    private var tscPrinter: TSCPrinter? = null
    private var printerHelper: PrinterHelper? = null
    private var decodedBitmap: Bitmap? = null
    private var rowsToPrint: Int = 1

    private fun exitAppAfterPrint() {
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        val base64 = intent?.getStringExtra("text_to_print")
        rowsToPrint = intent?.getStringExtra("rows_to_print")?.toIntOrNull() ?: 1

        if (!base64.isNullOrBlank()) {
            decodeAndDisplayBase64Image(base64)
            decodedBitmap?.let {
                printerHelper?.printBitmap(it, rowsToPrint)
                exitAppAfterPrint()
            } ?: showToast("ไม่สามารถพิมพ์ได้: ยังไม่มีรูป")
        } else {
            showToast("ไม่มีข้อมูลสำหรับพิมพ์")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestBluetoothPermissionsIfNeeded()
        printerHelper = PrinterHelper(this, App.get().curConnect)

        val previewBitmap = BitmapFactory.decodeResource(resources, R.drawable.test)
        binding.imageView.setImageBitmap(previewBitmap)
        updateConnectionStatus(false)
        binding.btnSearchPrinter.setOnClickListener { searchPrinter() }

        binding.btnPrintImage.setOnClickListener {
            printerHelper?.printBitmap(previewBitmap, rowsToPrint)
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun decodeAndDisplayBase64Image(base64String: String) {
        try {
            val pureBase64 = if (base64String.contains(",")) base64String.substringAfter(",") else base64String
            val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap != null) {
                decodedBitmap = bitmap
                binding.imageView.setImageBitmap(bitmap)
                binding.imagePreview.setImageBitmap(bitmap)
                showToast("พิมพ์สำเร็จแล้วครับ")
            } else {
                showToast("แปลง base64 ไม่สำเร็จ")
            }
        } catch (e: Exception) {
            Log.e("Base64", "Error decoding base64", e)
            showToast("เกิดข้อพลาดในการแปลง base64")
        }
    }

    private fun searchPrinter() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            showToast("กรุณาเปิด Bluetooth")
            return
        }

        val pairedDevices = adapter.bondedDevices.toList()
        pairedDevices.forEach {
            Log.d("Bluetooth", "พบเครื่องพิมพ์: ${it.name} - ${it.address}")
        }

        if (pairedDevices.isEmpty()) {
            showToast("ไม่พบเครื่องพิมพ์ที่จับคู่ไว้")
            return
        }

        val names = pairedDevices.map { it.name }
        AlertDialog.Builder(this)
            .setTitle("เลือกเครื่องพิมพ์")
            .setItems(names.toTypedArray()) { _, which ->
                connectToPrinter(pairedDevices[which])
            }
            .show()
    }

    private fun updateConnectionStatus(connected: Boolean, printerName: String = "") {
        if (connected) {
            binding.statusTextView.text = "เชื่อมต่อกับ $printerName แล้ว"
            binding.statusTextView.setTextColor(resources.getColor(android.R.color.black, theme))
        } else {
            binding.statusTextView.text = "สถานะ: ยังไม่ได้เชื่อมต่อ"
            binding.statusTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
        }
    }

    private fun connectToPrinter(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("Permission", "BLUETOOTH_CONNECT ยังไม่ได้รับตอน connect")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            return
        }

        Log.d("Bluetooth", "กำลังเชื่อมต่อกับ: ${device.name} - ${device.address}")

        val connection = POSConnect.createDevice(POSConnect.DEVICE_TYPE_BLUETOOTH)
        if (connection == null) {
            Log.e("Bluetooth", "POSConnect.createDevice return null")
            showToast("สร้างอุปกรณ์ Bluetooth ไม่สำเร็จ")
            return
        }

        App.get().curConnect = connection

        App.get().curConnect?.connect(device.address) { code, msg ->
            runOnUiThread {
                Log.d("Bluetooth", "connect result code=$code, msg=$msg")

                if (code == POSConnect.CONNECT_SUCCESS) {
                    tscPrinter = TSCPrinter(App.get().curConnect)
                    printerHelper = PrinterHelper(this, App.get().curConnect)
                    updateConnectionStatus(true, device.name)
                    showToast("เชื่อมต่อสำเร็จ")

                    intent?.getStringExtra("text_to_print")?.let {
                        decodeAndDisplayBase64Image(it)
                        decodedBitmap?.let { bitmap ->
                            printerHelper?.printBitmap(bitmap, rowsToPrint)
                            exitAppAfterPrint()
                        }
                    }

                } else {
                    updateConnectionStatus(false)
                    showToast("เชื่อมต่อไม่สำเร็จ: $msg")
                }
            }
        }
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val notGranted = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (notGranted.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
