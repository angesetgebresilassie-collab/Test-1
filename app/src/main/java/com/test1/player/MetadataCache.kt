package com.test1.player
import android.content.Context
import org.json.JSONObject
class MetadataCache(context: Context) {
 private val p=context.getSharedPreferences("metadata_cache",0)
 fun get(id:Long):CachedMetadata?=p.getString(id.toString(),null)?.let{runCatching{val o=JSONObject(it);CachedMetadata(o.optString("title"),o.optString("artist"),o.optString("album"),o.optString("artwork").ifBlank{null},o.optString("lyrics").ifBlank{null})}.getOrNull()}
 fun put(id:Long,m:CachedMetadata){p.edit().putString(id.toString(),JSONObject().put("title",m.title).put("artist",m.artist).put("album",m.album).put("artwork",m.artworkUrl?:"").put("lyrics",m.lyrics?:"").toString()).apply()}
}
data class CachedMetadata(val title:String,val artist:String,val album:String,val artworkUrl:String?,val lyrics:String?)
