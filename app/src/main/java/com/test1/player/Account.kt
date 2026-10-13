package com.test1.player

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Account tab's data: a display name, an avatar, and a background image with adjustable blur.
 * Images are copied (downscaled) into the app's private storage, so they keep working after the
 * original photo is moved or deleted. Everything is saved in SharedPreferences + files.
 *
 * Each value is its own flow so that, for example, dragging the blur slider or typing a name only
 * recomposes the piece of UI that shows it, not the whole Home screen.
 */
class AccountViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val dir = File(app.filesDir, "account")

    val name = MutableStateFlow(prefs.getString("name", "") ?: "")
    val blurDp = MutableStateFlow(prefs.getFloat("blur", 16f))
    val avatarPath = MutableStateFlow(existing(prefs.getString("avatar", null)))
    val backgroundPath = MutableStateFlow(existing(prefs.getString("background", null)))

    private fun existing(path: String?): String? = path?.takeIf { File(it).exists() }

    fun setName(value: String) {
        name.value = value
        prefs.edit().putString("name", value).apply()
    }

    /** Live while the slider moves; [saveBlur] persists it when the finger lifts. */
    fun setBlur(value: Float) {
        blurDp.value = value
    }

    fun saveBlur() {
        prefs.edit().putFloat("blur", blurDp.value).apply()
    }

    fun setAvatar(uri: Uri) = import(uri, "avatar", 512) { avatarPath.value = it }
    fun setBackground(uri: Uri) = import(uri, "background", 1600) { backgroundPath.value = it }
    fun clearAvatar() = remove("avatar") { avatarPath.value = null }
    fun clearBackground() = remove("background") { backgroundPath.value = null }

    private fun import(uri: Uri, kind: String, maxPx: Int, publish: (String?) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { copyDownscaled(uri, kind, maxPx) } ?: return@launch
            val old = prefs.getString(kind, null)
            prefs.edit().putString(kind, path).apply()
            publish(path)
            if (old != null && old != path) withContext(Dispatchers.IO) { File(old).delete() }
        }
    }

    private fun remove(kind: String, publish: () -> Unit) {
        val old = prefs.getString(kind, null)
        prefs.edit().remove(kind).apply()
        publish()
        if (old != null) viewModelScope.launch(Dispatchers.IO) { File(old).delete() }
    }

    /** Decodes [uri] at a safe size, fixes the photo's rotation and saves it as a JPEG. */
    private fun copyDownscaled(uri: Uri, kind: String, maxPx: Int): String? = runCatching {
        val resolver = app.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("could not decode image")

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val rotated = if (degrees != 0f) {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(degrees) }, true,
            )
        } else decoded

        val longest = maxOf(rotated.width, rotated.height)
        val final = if (longest > maxPx) {
            val scale = maxPx.toFloat() / longest
            Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * scale).toInt().coerceAtLeast(1),
                (rotated.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else rotated

        dir.mkdirs()
        // A new file name each time, so the screen reloads even when the user re-picks an image.
        val file = File(dir, "$kind-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }.getOrNull()
}

/** Loads a saved image file off the main thread. Keeps showing the previous image while a new one loads. */
@Composable
fun rememberFileImage(path: String?, maxPx: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, path, maxPx) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return image
}

/** The accent colour of the background image (null for no image or greyscale art). */
@Composable
fun rememberBackgroundAccent(background: ImageBitmap?): Color? {
    val accent by produceState<Color?>(initialValue = null, background) {
        value = if (background == null) {
            null
        } else {
            withContext(Dispatchers.Default) { background.accentColor() }
        }
    }
    return accent
}

private fun Modifier.softBlur(blurDp: Float): Modifier =
    if (blurDp >= 0.5f) blur(blurDp.dp, BlurredEdgeTreatment.Unbounded) else this

/**
 * The app-wide background: the user's image blurred by the chosen amount under a dark scrim, or
 * the plain dark colour when none is set. Reads the blur itself, so dragging the slider only
 * redraws this layer. (Compose can only blur on Android 12+; older phones show it sharp.)
 */
@Composable
fun AppBackground(bitmap: ImageBitmap?, account: AccountViewModel) {
    val blur by account.blurDp.collectAsState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().softBlur(blur),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
    }
}

@Composable
fun AccountScreen(
    vm: AccountViewModel,
    avatar: ImageBitmap?,
    background: ImageBitmap?,
    bottomPad: Dp,
) {
    val name by vm.name.collectAsState()
    val blur by vm.blurDp.collectAsState()
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setAvatar(uri)
    }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setBackground(uri)
    }
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Avatar + name
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = cardColors) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { pickAvatar.launch(imageOnly) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatar != null) {
                        Image(
                            bitmap = avatar,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Choose avatar",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pickAvatar.launch(imageOnly) }) {
                        Text(if (avatar == null) "Choose photo" else "Change photo")
                    }
                    if (avatar != null) {
                        TextButton(onClick = vm::clearAvatar) { Text("Remove") }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = vm::setName,
                    singleLine = true,
                    label = { Text("Your name") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Background + blur
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = cardColors) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Background",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { pickBackground.launch(imageOnly) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (background != null) {
                        Image(
                            bitmap = background,
                            contentDescription = "Background preview",
                            modifier = Modifier.fillMaxSize().softBlur(blur),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            "Tap to choose a background",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Blur · ${blur.toInt()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = blur,
                    onValueChange = vm::setBlur,
                    onValueChangeFinished = vm::saveBlur,
                    valueRange = 0f..40f,
                    enabled = background != null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pickBackground.launch(imageOnly) }) {
                        Text(if (background == null) "Choose background" else "Change background")
                    }
                    if (background != null) {
                        TextButton(onClick = vm::clearBackground) { Text("Remove") }
                    }
                }
                Text(
                    "The app's colour comes from this background until you play a song.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
