package es.pascuord.commits

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var user: EditText
    private lateinit var token: EditText
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        user = findViewById(R.id.user)
        token = findViewById(R.id.token)
        status = findViewById(R.id.status)
        preview = findViewById(R.id.preview)

        user.setText(Store.user(this))

        findViewById<Button>(R.id.save).setOnClickListener {
            val u = user.text.toString().trim().removePrefix("@")
            if (u.isEmpty()) {
                status.text = "Escribe tu usuario de GitHub."
                return@setOnClickListener
            }
            val t = token.text.toString().trim()
            Store.saveSettings(this, u, t.ifEmpty { null })
            token.setText("")
            Refresh.schedule(this)
            Refresh.now(this)
            status.text = "Actualizando…"
        }

        findViewById<Button>(R.id.pin).setOnClickListener {
            val manager = getSystemService(AppWidgetManager::class.java)
            if (manager != null && manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(ComponentName(this, CommitsWidget::class.java), null, null)
            } else {
                Toast.makeText(
                    this,
                    "Mantén pulsado el fondo de la pantalla de inicio → Widgets → Mis Commits",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Refresh.schedule(this)
        val stale = System.currentTimeMillis() - Store.lastUpdate(this) > 60 * 60 * 1000
        if (Store.user(this).isNotBlank() && stale) Refresh.now(this)
        handler.post(poll)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(poll)
    }

    private fun refreshUi() {
        token.hint = if (Store.token(this).isNotBlank()) "Token guardado ••••" else "ghp_…"

        val density = resources.displayMetrics.density
        val w = preview.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = preview.height.takeIf { it > 0 } ?: (140 * density).toInt()
        preview.setImageBitmap(
            Renderer.render(Store.data(this), Store.user(this), w, h, density, CommitsWidget.isDark(this))
        )

        val error = Store.lastError(this)
        val updated = Store.lastUpdate(this)
        if (status.text.toString() == "Escribe tu usuario de GitHub.") return
        status.text = when {
            error.isNotBlank() -> error
            updated > 0 -> {
                val hour = SimpleDateFormat("HH:mm", Locale("es", "ES")).format(Date(updated))
                val source = if (Store.token(this).isNotBlank()) "API oficial, con repos privados" else "datos públicos"
                "Actualizado a las $hour · $source. El widget se refresca cada hora."
            }
            else -> status.text
        }
    }
}
