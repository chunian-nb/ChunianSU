// SPDX-License-Identifier: GPL-3.0-only
package me.weishu.kernelsu.ui.privacy

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Changes only our launcher alias. This does not hide a package, UID, files or processes. */
object LauncherPrivacy {
    private fun alias(context: Context) =
        ComponentName(context.packageName, "${context.packageName}.LauncherAlias")

    fun isHidden(context: Context): Boolean =
        when (context.packageManager.getComponentEnabledSetting(alias(context))) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> true
            else -> false // DEFAULT uses android:enabled="true" from the manifest.
        }

    fun setHidden(context: Context, hidden: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            alias(context),
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
