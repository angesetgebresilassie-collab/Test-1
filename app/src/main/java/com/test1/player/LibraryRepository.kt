package com.test1.player
import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
class LibraryRepository(private val resolver:ContentResolver){
 fun songs():List<Song>{
  val out=mutableListOf<Song>();val uri=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  val p=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION)
  resolver.query(uri,p,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC")?.use{c->
   val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val t=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);val a=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);val al=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);val d=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
   while(c.moveToNext()){val x=c.getLong(id);out+=Song(x,c.getString(t)?:"Unknown title",c.getString(a)?.takeUnless{it=="<unknown>"}?:"Unknown artist",c.getString(al)?:"Unknown album",c.getLong(d),ContentUris.withAppendedId(uri,x))}
  };return out
 }
}
