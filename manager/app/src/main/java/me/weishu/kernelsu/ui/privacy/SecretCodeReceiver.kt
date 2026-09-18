// SPDX-License-Identifier: GPL-3.0-only
package me.weishu.kernelsu.ui.privacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import me.weishu.kernelsu.ui.MainActivity

/** Standard Android dialer code: *#*#888#*#*. No call interception or phone permission. */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val data = intent.data ?: return
        if (data.scheme != "android_secret_code" || data.host != "888") return
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return

        // Some Android releases send both the legacy and current protected broadcasts.
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchMillis < 1500L) return
        lastLaunchMillis = now

        try {
            // Always target the real activity, never the possibly disabled launcher alias.
            // Do not forward any caller-provided URI, command or extras to a root manager.
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (error: RuntimeException) {
            Log.w("ChunianSU", "Could not open the manager from the dialer", error)
        }
        // A successful startActivity call does not prove that an OEM allowed the launch.
        // The user must test their real dialer before disabling the launcher alias.
    }

    companion object {
        private val ACTIONS = setOf(
            "android.provider.Telephony.SECRET_CODE",
            "android.telephony.action.SECRET_CODE",
        )
        private var lastLaunchMillis = -1500L
    }
}
