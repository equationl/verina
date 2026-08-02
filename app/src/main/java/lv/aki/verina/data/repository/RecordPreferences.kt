package lv.aki.verina.data.repository

import android.content.Context
import androidx.core.content.edit

class RecordPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    var keepAllTransferRecords: Boolean
        get() = preferences.getBoolean(KEY_KEEP_ALL_TRANSFER_RECORDS, false)
        set(value) {
            preferences.edit { putBoolean(KEY_KEEP_ALL_TRANSFER_RECORDS, value) }
        }

    companion object {
        private const val PREFERENCES_NAME = "record_preferences"
        private const val KEY_KEEP_ALL_TRANSFER_RECORDS = "keep_all_transfer_records"
    }
}
