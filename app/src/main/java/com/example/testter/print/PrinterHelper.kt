package com.example.testter.print

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import net.posprinter.IDeviceConnection
import net.posprinter.TSCPrinter

class PrinterHelper(private val context: Context, private val binder: IDeviceConnection?) {


    fun printBitmap(bitmap: Bitmap ,copies:Int = 1) {
        if (binder == null) {
            Toast.makeText(context, "Printer not connected", Toast.LENGTH_SHORT).show()
            return
        }

        val dpi = 8
        val labelWidthMm = 32.0   // 3.2 ซม.
        val labelHeightMm = 25.0  // 2.5 ซม.
        val gapXmm = 1.3          // ช่องว่างซ้าย-ขวา เพื่อให้รวมกันเป็น 10 ซม. (3*3.2 + 2*1.3 = 10)

        val cols = 3
        val rows = 1 // พิมพ์แค่ 1 แถว

        val labelWidthPx = (labelWidthMm * dpi).toInt()
        val labelHeightPx = (labelHeightMm * dpi).toInt()
        val gapXpx = (gapXmm * dpi).toInt()

        // ขนาดภาพที่ต้องการ
        val targetWidth = cols * labelWidthPx + (cols - 1) * gapXpx
        val targetHeight = labelHeightPx

        // Resize และ crop ให้พอดี
        val scaledBitmap = resizeAndCropToLabelGrid(bitmap, cols, rows, labelWidthPx, labelHeightPx, gapXpx, 0)
        val monoBitmap = convertToMonochrome(scaledBitmap)

        val tscPrinter = TSCPrinter(binder)
        val paperWidthMm = cols * labelWidthMm + (cols - 1) * gapXmm

        repeat(copies) {
            tscPrinter
                .sizeMm(paperWidthMm, labelHeightMm)
                .gapMm(gapXmm, 0.0)
                .density(8)
                .cls()

            for (col in 0 until cols) {
                val x = col * (labelWidthPx + gapXpx)
                val label = Bitmap.createBitmap(monoBitmap, x, 0, labelWidthPx, labelHeightPx)
                tscPrinter.bitmap(x, 0, 0, label.width, label)
            }

            tscPrinter.print()
        }
    }

    private fun resizeAndCropToLabelGrid(
        bitmap: Bitmap,
        cols: Int,
        rows: Int,
        labelWidthPx: Int,
        labelHeightPx: Int,
        gapXpx: Int,
        gapYpx: Int
    ): Bitmap {
        val targetWidth = cols * labelWidthPx + (cols - 1) * gapXpx
        val targetHeight = rows * labelHeightPx + (rows - 1) * gapYpx

        val scale = maxOf(
            targetWidth.toFloat() / bitmap.width,
            targetHeight.toFloat() / bitmap.height
        )
        val resized = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        )

        val offsetX = (resized.width - targetWidth) / 2
        val offsetY = (resized.height - targetHeight) / 2

        return Bitmap.createBitmap(resized, offsetX, offsetY, targetWidth, targetHeight)
    }

    private fun sendCommands(commands: List<ByteArray>) {
        if (binder == null) {
            Log.e("PrinterHelper", "No connection")
            Toast.makeText(context, "Printer not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            for (cmd in commands) {
                binder.sendData(cmd)
                Thread.sleep(50)
            }
        } catch (e: Exception) {
            Log.e("PrinterHelper", "Send error: ${e.message}", e)
        }
    }

    private fun convertToMonochrome(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val monoBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val threshold = 200 // เพิ่ม threshold ให้สูงขึ้น จะได้พื้นหลังขาวมากขึ้น
        for (i in pixels.indices) {
            val color = pixels[i]
            val a = Color.alpha(color)
            if (a < 128) {
                pixels[i] = Color.WHITE // ถ้าโปร่งใส ให้เป็นสีขาว
                continue
            }

            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val gray = (0.3 * r + 0.59 * g + 0.11 * b).toInt()
            pixels[i] = if (gray < threshold) Color.BLACK else Color.WHITE
        }

        monoBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return monoBitmap
    }

}