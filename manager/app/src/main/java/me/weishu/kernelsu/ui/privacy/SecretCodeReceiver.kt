// SPDX-License-Identifier: GPL-3.0-only
package me.weishu.kernelsu.ui.privacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import me.weishu.kernelsu.ui.MainActivity

/** Standard Android secret code: *#*#888#*#*. No call interception or phone permission. */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val data = intent.data ?: return
        if (data.scheme != "android_secret_code" || data.host != "888") return
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchMillis < 1500L) return
        lastLaunchMillis = now

        try {
            // Treat the dialer code as a recovery action: restore the launcher entry first,
            // then open the real manager. OEM dialers may choose not to dispatch this code.
            LauncherPrivacy.setHidden(context, false)
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (error: RuntimeException) {
            Log.w("ChunianSU", "Could not recover/open the manager from the dialer", error)
        }
    }

    companion object {
        private val ACTIONS = setOf(
            "android.provider.Telephony.SECRET_CODE",
            "android.telephony.action.SECRET_CODE",
        )
        private var lastLaunchMillis = -1500L
    }
}
