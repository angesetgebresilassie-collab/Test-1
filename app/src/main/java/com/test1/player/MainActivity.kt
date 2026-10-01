package com.test1.player
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContent{val vm:PlayerViewModel=viewModel();val songs by vm.songs.collectAsState();val st by vm.state.collectAsState();var ok by remember{mutableStateOf(Build.VERSION.SDK_INT<33)};val ask=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ok=it;if(it)vm.load()};LaunchedEffect(Unit){if(Build.VERSION.SDK_INT>=33&&!ok)ask.launch(Manifest.permission.READ_MEDIA_AUDIO)else vm.load()};MaterialTheme(colorScheme=darkColorScheme()){Surface(Modifier.fillMaxSize(),color=Color(0xFF090A0F)){if(ok){LaunchedEffect(Unit){vm.connect()};Home(songs,st,vm)}else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Button({ask.launch(Manifest.permission.READ_MEDIA_AUDIO)}){Text("Allow music access")}}}}}}
}
@Composable fun Home(songs:List<Song>,st:PlayerState,vm:PlayerViewModel){Column(Modifier.fillMaxSize().padding(18.dp)){Text("Your Music",style=MaterialTheme.typography.headlineLarge);Text(songs.size.toString()+" songs",color=Color.White.copy(.6f));Spacer(Modifier.height(14.dp));st.current?.let{Glass{Text(st.metadata?.title?:it.title,style=MaterialTheme.typography.titleLarge);Text(st.metadata?.artist?:it.artist,color=Color.White.copy(.7f));Row(verticalAlignment=Alignment.CenterVertically){IconButton(vm::toggle){Icon(if(st.playing)Icons.Default.Pause else Icons.Default.PlayArrow,null)};Text(st.metadata?.album?:it.album,color=Color.White.copy(.55f))};st.metadata?.lyrics?.let{Text(it.take(400),color=Color.White.copy(.7f))}};Spacer(Modifier.height(12.dp))};LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(songs,key={it.id}){s->Glass(Modifier.clickable{vm.play(s)}){Text(s.title,style=MaterialTheme.typography.titleMedium);Text(s.artist,color=Color.White.copy(.65f))}}}}}
@Composable fun Glass(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.10f)).padding(16.dp),content=content)}
