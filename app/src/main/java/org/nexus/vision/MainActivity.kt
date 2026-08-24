package org.nexus.vision

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
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

        val voidBlack = Color.parseColor("#07070B")
        val blood = Color.parseColor("#8B1E2B")
        val ember = Color.parseColor("#E8433A")
        val bone = Color.parseColor("#E9E6E0")
        val ash = Color.parseColor("#8D8A96")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 140, 60, 60)
            setBackgroundColor(voidBlack)
        }

        val title = TextView(this).apply {
            text = "NEXUS VISION"
            textSize = 26f
            setTextColor(bone)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(title)

        val subtitle = TextView(this).apply {
            text = "MODULE DE CAPTURE ET TRADUCTION"
            textSize = 11f
            setTextColor(ash)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
            letterSpacing = 0.15f
        }
        layout.addView(subtitle)

        val info = TextView(this).apply {
            text = "1. Autorise l'affichage par-dessus les autres apps\n\n" +
                    "2. Active le service d'accessibilité\n\n" +
                    "3. Lance la sélection à l'écran"
            textSize = 15f
            setTextColor(ash)
            setPadding(20, 0, 20, 60)
        }
        layout.addView(info)

        fun styledButton(label: String, bg: Int, textColor: Int): Button {
            return Button(this).apply {
                text = label
                setTextColor(textColor)
                textSize = 15f
                isAllCaps = false
                val shape = GradientDrawable().apply {
                    cornerRadius = 24f
                    setColor(bg)
                    setStroke(2, blood)
                }
                background = shape
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    180
                )
                params.setMargins(0, 0, 0, 30)
                layoutParams = params
            }
        }

        val btnOverlay = styledButton("1 · AUTORISER L'OVERLAY", voidBlack, ember).apply {
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
        layout.addView(btnOverlay)

        val btnAccessibility = styledButton("2 · ACTIVER L'ACCESSIBILITÉ", voidBlack, ember).apply {
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(btnAccessibility)

        val btnStart = styledButton("3 · LANCER LA SÉLECTION", blood, bone).apply {
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

