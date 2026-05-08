package com.example.ukfloodsdemographics

import android.app.Application
import com.google.android.gms.maps.MapsInitializer



class UKFloodsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, null)
    }
}
