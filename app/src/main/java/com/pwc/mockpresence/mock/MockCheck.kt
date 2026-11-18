package com.pwc.mockpresence.mock

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

object MockCheck {
    /** True when this app is chosen in Developer options > Select mock location app. */
    @Suppress("DEPRECATION")
    fun isMockApp(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return try {
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), ctx.packageName)
            } else {
                ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), ctx.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
}
