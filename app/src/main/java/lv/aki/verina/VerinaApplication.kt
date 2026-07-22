package lv.aki.verina

import android.app.Application
import lv.aki.verina.data.db.AppDatabase

class VerinaApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: VerinaApplication
            private set
    }
}
