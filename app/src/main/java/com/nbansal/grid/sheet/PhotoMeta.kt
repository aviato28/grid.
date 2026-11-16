package com.nbansal.grid.sheet

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

data class PhotoMeta(val date: LocalDate?, val location: String)

/** Best-effort EXIF read (date, GPS -> "City, Country"). Never throws. */
object PhotoMetaReader {
    suspend fun read(context: Context, uri: Uri): PhotoMeta = withContext(Dispatchers.IO) {
        var date: LocalDate? = null
        var latLong: DoubleArray? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val exif = ExifInterface(s)
                date = parseExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                    ?: parseExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME))
                latLong = exif.latLong
            }
        } catch (_: Exception) {
        }
        val place = latLong?.let { place(context, it[0], it[1]) } ?: ""
        PhotoMeta(date, place)
    }

    @Suppress("DEPRECATION")
    private fun place(context: Context, lat: Double, lon: Double): String? = try {
        if (!Geocoder.isPresent()) null else {
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            val city = a?.locality ?: a?.subAdminArea ?: a?.adminArea
            listOfNotNull(city, a?.countryName).joinToString(", ").ifEmpty { null }?.take(MAX_LOCATION)
        }
    } catch (_: Exception) {
        null
    }
}
