package com.github.countryman.broker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object BrokerHelper {
    private const val BROKER_PACKAGE = "com.github.countryman.broker"
    private const val BROKER_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    private val brokerComponent = ComponentName(BROKER_PACKAGE, "com.github.countryman.broker.BrokerInitActivity")

    fun isInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(BROKER_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun isReady(context: Context): Boolean {
        if (!isInstalled(context)) return false
        return context.packageManager.checkPermission(BROKER_PERMISSION, BROKER_PACKAGE) == PackageManager.PERMISSION_GRANTED
    }

    fun openSetup(context: Context): Boolean {
        val intent = Intent().apply {
            component = brokerComponent
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
