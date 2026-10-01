package com.test1.player
import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
data class PlayerState(val current:Song?=null,val metadata:CachedMetadata?=null,val playing:Boolean=false)
class PlayerViewModel(app:Application):AndroidViewModel(app){
 private val repo=LibraryRepository(app.contentResolver);private val meta=MetadataRepository(MetadataCache(app))
 val songs=MutableStateFlow<List<Song>>(emptyList());val state=MutableStateFlow(PlayerState()).asStateFlow();private var c:MediaController?=null
 init{load();viewModelScope.launch{c=MediaController.Builder(app,SessionToken(app,ComponentName(app,PlaybackService::class.java))).buildAsync().get();c?.addListener(object:androidx.media3.common.Player.Listener{override fun onIsPlayingChanged(v:Boolean){val s=state.value; (state as MutableStateFlow).value=s.copy(playing=v)};override fun onMediaItemTransition(i:androidx.media3.common.MediaItem?,r:Int){songs.value.firstOrNull{it.id.toString()==i?.mediaId}?.let{playMetadata(it)}}})}}
 fun load(){viewModelScope.launch{songs.value=repo.songs()}}
 fun play(s:Song){viewModelScope.launch{c?.setMediaItems(songs.value.map{it.toMediaItem()},songs.value.indexOf(s),0);c?.play();playMetadata(s)}}
 fun toggle(){c?.let{if(it.isPlaying)it.pause()else it.play()}}
 private fun playMetadata(s:Song){viewModelScope.launch{(state as MutableStateFlow).value=state.value.copy(current=s,metadata=meta.enrich(s))}}
 override fun onCleared(){c?.release();super.onCleared()}
}
