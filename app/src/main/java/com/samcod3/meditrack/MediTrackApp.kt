package com.samcod3.meditrack

import android.app.Application
import com.samcod3.meditrack.di.appModule
import com.samcod3.meditrack.di.dataModule
import com.samcod3.meditrack.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MediTrackApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@MediTrackApp)
            modules(
                appModule,
                dataModule,
                networkModule
            )
        }
    }
}
