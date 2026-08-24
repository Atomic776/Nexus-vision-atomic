package org.nexus.vision

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }

        val info = TextView(this).apply {
            text = "NEXUS Vision\n\n" +
                    "Étape 1 : autorise l'affichage par-dessus les autres apps\n" +
                    "Étape 2 : active le service d'accessibilité (pour les clics)\n" +
                    "Étape 3 : lance la sélection à l'écran"
            textSize = 15f
            setPadding(0, 0, 0, 60)
        }
        layout.addView(info)

        val btnOverlay = Button(this).apply {
            text = "1. Autoriser l'overlay"
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
        layout.addView(btnOverlay)

        val btnAccessibility = Button(this).apply {
            text = "2. Activer l'accessibilité"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(btnAccessibility)

        val btnStart = Button(this).apply {
            text = "3. Lancer la sélection à l'écran"
            setOnClickListener {
                val captureIntent = projectionManager.createScreenCaptureIntent()
                startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
            }
        }
        layout.addView(btnStart)

        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            ActivityCompat.startForegroundService(this, serviceIntent)
            moveTaskToBack(true)
        }
    }

    companion object {
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
    }
}
