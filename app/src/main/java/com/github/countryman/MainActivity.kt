package com.github.countryman

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.github.countryman.ui.i18n.AppLanguage
import com.github.countryman.ui.screens.AboutScreen
import com.github.countryman.ui.screens.MainScreen
import com.github.countryman.ui.screens.ShizukuNotReadyScreen
import com.github.countryman.ui.theme.CountrymanTheme
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var isShizukuReady by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)
    private var appLanguage by mutableStateOf(AppLanguage.ZH)
    private var brokerRefreshNonce by mutableStateOf(0)

    private val brokerResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_BROKER_RESULT) return
            val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)
            val clear = intent.getBooleanExtra(EXTRA_CLEAR, false)
            val error = intent.getStringExtra(EXTRA_ERROR)
            brokerRefreshNonce += 1
            val strings = com.github.countryman.ui.i18n.stringsFor(appLanguage)
            if (success) {
                Toast.makeText(
                    this@MainActivity,
                    if (clear) strings.resetDone else strings.saveDone,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    if (clear) strings.resetFailed(error) else strings.saveFailed(error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        appLanguage = prefs.getString("app_language", null)
            ?.let { AppLanguage.fromCode(it) }
            ?: detectSystemLanguage()

        // 初始化 Hidden API 访问
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        // 检查 Shizuku 状态
        checkShizukuStatus()

        // 添加 Shizuku 权限监听器
        Shizuku.addRequestPermissionResultListener { _, grantResult ->
            isShizukuReady = grantResult == PackageManager.PERMISSION_GRANTED
            if (!isShizukuReady) {
                Toast.makeText(this, com.github.countryman.ui.i18n.stringsFor(appLanguage).shizukuPermissionToast, Toast.LENGTH_LONG).show()
            }
        }

        // 添加 Shizuku 绑定监听器
        Shizuku.addBinderReceivedListener {
            checkShizukuStatus()
        }

        ContextCompat.registerReceiver(
            this,
            brokerResultReceiver,
            IntentFilter(ACTION_BROKER_RESULT),
            ContextCompat.RECEIVER_EXPORTED
        )

        setContent {
            CountrymanTheme {
                if (showAbout) {
                    AboutScreen(onBack = { showAbout = false }, language = appLanguage)
                } else if (isShizukuReady) {
                    MainScreen(
                        onShowAbout = { showAbout = true },
                        onClose = { finish() },
                        language = appLanguage,
                        refreshNonce = brokerRefreshNonce,
                            onLanguageChange = { language ->
                                appLanguage = language
                                getSharedPreferences("app_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putString("app_language", language.code)
                                    .apply()
                        }
                    )
                } else {
                    ShizukuNotReadyScreen(language = appLanguage)
                }
            }
        }
    }

    private fun checkShizukuStatus() {
        isShizukuReady = if (Shizuku.getBinder() == null) {
            Toast.makeText(this, com.github.countryman.ui.i18n.stringsFor(appLanguage).shizukuMissingToast, Toast.LENGTH_LONG).show()
            false
        } else {
            val hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Shizuku.requestPermission(0)
            }
            hasPermission
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(brokerResultReceiver)
        Shizuku.removeRequestPermissionResultListener { _, _ -> }
        Shizuku.removeBinderReceivedListener { }
    }

    private fun detectSystemLanguage(): AppLanguage {
        val languageTag = resources.configuration.locales[0]?.language.orEmpty()
        return when {
            languageTag.equals("zh", ignoreCase = true) -> AppLanguage.ZH
            languageTag.equals("en", ignoreCase = true) -> AppLanguage.EN
            else -> AppLanguage.EN
        }
    }

    companion object {
        private const val ACTION_BROKER_RESULT = "com.github.countryman.BROKER_RESULT"
        private const val EXTRA_SUCCESS = "success"
        private const val EXTRA_ERROR = "error"
        private const val EXTRA_CLEAR = "clear"
    }
}
