package es.pascuord.commits

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class CommitsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { draw(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        draw(context, manager, id)
    }

    override fun onEnabled(context: Context) {
        Refresh.schedule(context)
        Refresh.now(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(Refresh.PERIODIC)
    }

    companion object {
        fun isDark(ctx: Context): Boolean =
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            manager.getAppWidgetIds(ComponentName(ctx, CommitsWidget::class.java))
                .forEach { draw(ctx, manager, it) }
        }

        fun draw(ctx: Context, manager: AppWidgetManager, id: Int) {
            val opts = manager.getAppWidgetOptions(id)
            // En vertical el ancho real es el mínimo y el alto real el máximo
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 110
            val density = ctx.resources.displayMetrics.density
            val bmp = Renderer.render(
                Store.data(ctx), Store.user(ctx),
                (wDp * density).toInt(), (hDp * density).toInt(),
                density, isDark(ctx)
            )
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val views = RemoteViews(ctx.packageName, R.layout.widget).apply {
                setImageViewBitmap(R.id.image, bmp)
                setOnClickPendingIntent(R.id.root, open)
            }
            manager.updateAppWidget(id, views)
        }
    }
}

/** Actualización en segundo plano: cada hora y bajo demanda. */
object Refresh {
    const val PERIODIC = "refresh"
    const val NOW = "refresh-now"

    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(net)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun now(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(net).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val user = Store.user(ctx)
        if (user.isNotBlank()) {
            try {
                Store.saveData(ctx, GitHub.fetch(user, Store.token(ctx)))
            } catch (e: Exception) {
                Store.saveError(ctx, e.message ?: "No se pudo conectar con GitHub.")
            }
        }
        CommitsWidget.updateAll(ctx)
        return Result.success()
    }
}
