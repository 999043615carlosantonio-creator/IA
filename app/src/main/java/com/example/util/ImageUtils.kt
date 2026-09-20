package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageUtils {
  suspend fun uriToBase64(context: Context, uri: Uri, maxDimension: Int = 1024): Pair<String, String>? =
    withContext(Dispatchers.IO) {
      try {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null

        // Decode bounds first to prevent OOM
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        var inSampleSize = 1
        val origWidth = options.outWidth
        val origHeight = options.outHeight

        if (origHeight > maxDimension || origWidth > maxDimension) {
          val halfHeight = origHeight / 2
          val halfWidth = origWidth / 2
          while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
            inSampleSize *= 2
          }
        }

        val decodeOptions = BitmapFactory.Options().apply {
          this.inSampleSize = inSampleSize
        }

        val secondStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val bitmap = BitmapFactory.decodeStream(secondStream, null, decodeOptions)
        secondStream.close()

        if (bitmap == null) return@withContext null

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

        Pair("image/jpeg", base64String)
      } catch (e: Exception) {
        null
      }
    }
}
