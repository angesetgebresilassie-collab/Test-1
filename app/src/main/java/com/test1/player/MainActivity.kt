package com.test1.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: PlayerViewModel = viewModel()
            val songs by vm.songs.collectAsState()
            val st by vm.state.collectAsState()
            var granted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33) }
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                granted = ok
                if (ok) vm.load()
            }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 && !granted) {
                    ask.launch(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    vm.load()
                }
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF090A0F)) {
                    if (granted) {
                        LaunchedEffect(Unit) { vm.connect() }
                        Home(songs, st, vm)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Button(onClick = { ask.launch(Manifest.permission.READ_MEDIA_AUDIO) }) {
                                Text("Allow music access")
                            }
                        }
                    }
                }
            }
        }
    }
}
