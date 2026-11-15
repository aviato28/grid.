package com.nbansal.grid.ui

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.nbansal.grid.sheet.MAX_PHOTOS

/** Returns a function that opens the system photo picker (images only, up to [MAX_PHOTOS]). */
@Composable
fun rememberPhotoPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val current by rememberUpdatedState(onPicked)
    val max = if (Build.VERSION.SDK_INT >= 33) minOf(MAX_PHOTOS, MediaStore.getPickImagesMaxLimit()) else MAX_PHOTOS
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(max)) { uris ->
        if (uris.isNotEmpty()) current(uris)
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
