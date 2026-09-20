// SPDX-License-Identifier: GPL-3.0-only
package me.weishu.kernelsu.ui.privacy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import me.weishu.kernelsu.ui.MainActivity

/**
 * Non-launcher recovery entry used when an OEM dialer does not dispatch Android secret codes.
 * Opening chuniansu://recover restores the launcher alias and then opens the real manager.
 */
class RecoveryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recoverAndOpen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recoverAndOpen()
    }

    private fun recoverAndOpen() {
        runCatching { LauncherPrivacy.setHidden(this, false) }
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }
}
