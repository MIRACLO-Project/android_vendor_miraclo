// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0

package org.miraclo.dolby

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Starts with the phone (a persistent system app) and keeps the sound settings applied. */
class DolbyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Dolby.init(this)
    }
}

/** Only makes sure the app process is running after boot; DolbyApp does the work. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Dolby.init(context)
    }
}

/** Quick Settings tile: Dolby Atmos on or off. Holding it opens Mira Dolby. */
class DolbyTile : TileService() {

    private val update: () -> Unit = { refresh() }

    override fun onStartListening() {
        Dolby.init(this)
        Dolby.addListener(update)
        refresh()
    }

    override fun onStopListening() {
        Dolby.removeListener(update)
    }

    override fun onClick() {
        Dolby.setEnabled(!Dolby.enabled)
    }

    private fun refresh() {
        val t = qsTile ?: return
        t.label = "Dolby Atmos"
        t.state = when {
            !Dolby.available -> Tile.STATE_UNAVAILABLE
            Dolby.enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        t.subtitle = when {
            !Dolby.available -> "Not available"
            !Dolby.enabled -> "Off"
            else -> Dolby.activePreset?.name ?: Dolby.kind.label
        }
        t.updateTile()
    }

}
