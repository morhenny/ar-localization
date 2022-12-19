package de.morhenn.ar_localization

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.morhenn.ar_localization.utils.DataExport

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        DataExport.init(this)
    }
}