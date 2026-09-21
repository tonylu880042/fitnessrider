package com.fitnessrider

import android.app.Application
import com.fitnessrider.data.ClassRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FitnessRiderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            ClassRepository(this@FitnessRiderApp).seedSampleClassIfEmpty()
        }
    }
}
