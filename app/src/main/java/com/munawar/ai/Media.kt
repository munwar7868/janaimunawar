package com.munawar.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream

object Media {
    fun bitmapToB64(bmp: Bitmap, maxDim: Int = 1600): String {
        var b = bmp
        val m = maxOf(b.width, b.height)
        if (m > maxDim) {
            val s = maxDim.toFloat() / m
            b = Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
        }
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, o)
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    /** Blocking: call from Dispatchers.IO. Supports images, PDF and text files. Returns null if unsupported/too big. */
    fun load(ctx: Context, uri: Uri): Attachment? {
        val cr = ctx.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val name = try {
            cr.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else ""
            } ?: ""
        } catch (e: Exception) { "" }

        if (mime.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var s = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / s > 2400) s *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = s }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val deg = try {
                cr.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        6 -> 90; 3 -> 180; 8 -> 270; else -> 0
                    }
                } ?: 0
            } catch (e: Exception) { 0 }
            if (deg != 0) {
                val mx = Matrix().apply { postRotate(deg.toFloat()) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mx, true)
            }
            return Attachment("image/jpeg", bitmapToB64(bmp), name)
        }
        if (mime == "application/pdf" || mime.startsWith("text/")) {
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.size > 15_000_000) return null
            return Attachment(mime, Base64.encodeToString(bytes, Base64.NO_WRAP), name)
        }
        return null
    }
}
