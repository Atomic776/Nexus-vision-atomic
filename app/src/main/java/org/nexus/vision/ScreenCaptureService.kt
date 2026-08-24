package org.nexus.vision

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var windowManager: WindowManager
    private var selectionView: SelectionOverlayView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        showSelectionOverlay()
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "nexus_vision_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "NEXUS Vision", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NEXUS Vision actif")
            .setContentText("Sélectionne une zone à l'écran")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    private fun showSelectionOverlay() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        selectionView = SelectionOverlayView(this) { rect ->
            windowManager.removeView(selectionView)
            captureAndProcess(rect.left, rect.top, rect.width(), rect.height(), metrics)
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(selectionView, params)
    }

    private fun captureAndProcess(x: Int, y: Int, width: Int, height: Int, metrics: DisplayMetrics) {
        val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "NexusCapture",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            val image = reader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * metrics.widthPixels

                var bitmap = Bitmap.createBitmap(
                    metrics.widthPixels + rowPadding / pixelStride,
                    metrics.heightPixels, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val safeWidth = minOf(width, bitmap.width - x)
                val safeHeight = minOf(height, bitmap.height - y)
                if (safeWidth > 0 && safeHeight > 0) {
                    bitmap = Bitmap.createBitmap(bitmap, x, y, safeWidth, safeHeight)
                }

                runOCR(bitmap)
            }
            virtualDisplay?.release()
            reader.close()
        }, 300)
    }

    private fun runOCR(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                if (text.isBlank()) {
                    showResultOverlay("Aucun texte détecté dans la zone sélectionnée.")
                    stopSelf()
                } else {
                    detectLanguageAndTranslate(text)
                }
            }
            .addOnFailureListener {
                showResultOverlay("Erreur OCR : ${it.message}")
                stopSelf()
            }
    }

    private fun detectLanguageAndTranslate(text: String) {
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode == "und") {
                    translateWith(TranslateLanguage.ENGLISH, text)
                } else {
                    val sourceLang = TranslateLanguage.fromLanguageTag(languageCode)
                    if (sourceLang == null) {
                        showResultOverlay("Langue non supportée pour la traduction.\n\nTexte original :\n$text")
                        stopSelf()
                    } else if (sourceLang == TranslateLanguage.FRENCH) {
                        showResultOverlay("Texte (déjà en français) :\n$text")
                        stopSelf()
                    } else {
                        translateWith(sourceLang, text)
                    }
                }
            }
            .addOnFailureListener {
                showResultOverlay("Détection de langue impossible.\n\nTexte original :\n$text")
                stopSelf()
            }
    }

    private fun translateWith(sourceLang: String, text: String) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(TranslateLanguage.FRENCH)
            .build()
        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated ->
                        showResultOverlay("Original :\n$text\n\nTraduction :\n$translated")
                        translator.close()
                        stopSelf()
                    }
                    .addOnFailureListener {
                        showResultOverlay("Erreur de traduction : ${it.message}\n\nTexte original :\n$text")
                        translator.close()
                        stopSelf()
                    }
            }
            .addOnFailureListener {
                showResultOverlay("Téléchargement du modèle de langue impossible (vérifie ta connexion).\n\nTexte original :\n$text")
                stopSelf()
            }
    }

    private fun showResultOverlay(text: String) {
        Handler(Looper.getMainLooper()).post {
            val resultView = FrameLayout(this)
            val textView = TextView(this).apply {
                this.text = text
                setPadding(40, 40, 40, 40)
                setBackgroundColor(0xEE101018.toInt())
                setTextColor(0xFFE9E6E0.toInt())
                textSize = 15f
            }
            resultView.addView(textView)

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }

            resultView.setOnClickListener { windowManager.removeView(resultView) }
            windowManager.addView(resultView, params)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?) = null
}
