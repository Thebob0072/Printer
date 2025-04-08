package com.example.testter

import android.app.Application
import android.util.Log
import net.posprinter.IDeviceConnection
import net.posprinter.POSConnect
import net.posprinter.IPOSListener

class App : Application() {

    var curConnect: IDeviceConnection? = null

    companion object {
        private lateinit var instance: App
        fun get(): App = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        POSConnect.init(this)
    }




}
