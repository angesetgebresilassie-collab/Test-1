package com.test1.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so keep system bar icons light regardless of the phone's theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val vm: PlayerViewModel = viewModel()
            val ctx = LocalContext.current

            // Google Sans is fetched from Google Fonts on first launch, then cached on disk.
            var fontFamily by remember { mutableStateOf<FontFamily?>(null) }
            LaunchedEffect(Unit) { fontFamily = AppFont.load(ctx.applicationContext) }
            val typography = remember(fontFamily) {
                fontFamily?.let { Typography().withFontFamily(it) } ?: Typography()
            }

            // Android 13+ has a dedicated audio permission; older versions need storage access.
            val audioPermission =
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE

            var granted by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED
                )
            }

            val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val askAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                granted = ok
            }

            LaunchedEffect(granted) {
                if (granted) {
                    vm.connect()
                    vm.load()
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    askAudio.launch(audioPermission)
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(), typography = typography) {
                // contentColor: a custom background colour has no matching content colour, which
                // left every Text without an explicit colour black on the dark background.
                Surface(Modifier.fillMaxSize(), color = Color(0xFF05060A), contentColor = Color.White) {
                    Home(
                        vm = vm,
                        granted = granted,
                        onGrant = { askAudio.launch(audioPermission) },
                        onOpenSettings = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", ctx.packageName, null))
                            )
                        },
                    )
                }
            }
        }
    }
}
