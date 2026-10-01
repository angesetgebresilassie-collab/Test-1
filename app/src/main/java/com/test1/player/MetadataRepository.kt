package com.test1.player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
class MetadataRepository(private val cache:MetadataCache){
 suspend fun enrich(song:Song)=withContext(Dispatchers.IO){
  cache.get(song.id)?.let{return@withContext it}
  val i=runCatching{itunes(song)}.getOrNull()
  val title=i?.title?.takeIf{it.isNotBlank()}?:song.title
  val artist=i?.artist?.takeIf{it.isNotBlank()}?:song.artist
  val album=i?.album?.takeIf{it.isNotBlank()}?:song.album
  val lyrics=runCatching{lyrics(artist,title)}.getOrNull()
  CachedMetadata(title,artist,album,i?.artworkUrl,lyrics).also{cache.put(song.id,it)}
 }
 private fun itunes(s:Song):CachedMetadata{
  val q=URLEncoder.encode(s.artist+" "+s.title,"UTF-8")
  val o=JSONObject(get("https://itunes.apple.com/search?term="+q+"&entity=song&limit=1")).getJSONArray("results").getJSONObject(0)
  return CachedMetadata(o.optString("trackName"),o.optString("artistName"),o.optString("collectionName"),o.optString("artworkUrl100").replace("100x100","600x600"),null)
 }
 private fun lyrics(a:String,t:String):String?{
  val u="https://lrclib.net/api/get?artist_name="+URLEncoder.encode(a,"UTF-8")+"&track_name="+URLEncoder.encode(t,"UTF-8")
  val o=JSONObject(get(u));return o.optString("plainLyrics").ifBlank{null}?:o.optString("syncedLyrics").ifBlank{null}
 }
 private fun get(u:String):String{val c=URL(u).openConnection() as HttpURLConnection;c.connectTimeout=8000;c.readTimeout=8000;return try{if(c.responseCode !in 200..299) throw IllegalStateException("HTTP "+c.responseCode);c.inputStream.bufferedReader().use{it.readText()}}finally{c.disconnect()}}
}
