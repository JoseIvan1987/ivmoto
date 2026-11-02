// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of IVMoto, derived from OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package app.ivmoto

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Home-screen widget: one tap opens IVMoto and connects to your bike. */
class ConnectWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, ConnectActivity::class.java)
                .putExtra(ConnectActivity.EXTRA_AUTO, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_connect)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(id, views)
        }
    }
}

/** Long-press shortcuts on the launcher icon: connect, map and trips. */
object Shortcuts {
    fun publish(ctx: Context) {
        val icon = IconCompat.createWithResource(ctx, R.mipmap.ic_launcher)
        fun shortcut(id: String, label: Int, target: Class<*>, auto: Boolean) =
            ShortcutInfoCompat.Builder(ctx, id)
                .setShortLabel(ctx.getString(label))
                .setIcon(icon)
                .setIntent(
                    Intent(ctx, target).setAction(Intent.ACTION_VIEW).apply {
                        if (auto) putExtra(ConnectActivity.EXTRA_AUTO, true)
                    },
                )
                .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                ctx,
                listOf(
                    shortcut("connect", R.string.shortcut_connect, ConnectActivity::class.java, true),
                    shortcut("map", R.string.home_tile_map, MapActivity::class.java, false),
                    shortcut("trips", R.string.shortcut_trips, TripsActivity::class.java, false),
                ),
            )
        }
    }
}
