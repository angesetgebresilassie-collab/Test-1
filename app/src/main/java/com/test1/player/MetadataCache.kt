package com.test1.player
import android.content.Context
import org.json.JSONObject

class MetadataCache(context: Context) {
 private val p=context.getSharedPreferences("metadata_cache",0)
 // v4: entries made before the order-independent file-name matching are stale and get re-fetched.
 fun get(id:Long):CachedMetadata?=p.getString(id.toString(),null)?.let{runCatching{
  val o=JSONObject(it)
  if(o.optInt("v",1)<4) return@runCatching null
  CachedMetadata(o.optString("title"),o.optString("artist"),o.optString("album"),o.optString("artwork").ifBlank{null},o.optString("lyrics").ifBlank{null},o.optString("synced").ifBlank{null})
 }.getOrNull()}
 fun put(id:Long,m:CachedMetadata){p.edit().putString(id.toString(),JSONObject().put("v",4).put("title",m.title).put("artist",m.artist).put("album",m.album).put("artwork",m.artworkUrl?:"").put("lyrics",m.lyrics?:"").put("synced",m.synced?:"").toString()).apply()}
}
data class CachedMetadata(val title:String,val artist:String,val album:String,val artworkUrl:String?,val lyrics:String?,val synced:String?=null)
