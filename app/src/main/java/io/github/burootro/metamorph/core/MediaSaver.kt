package io.github.burootro.metamorph.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object MediaSaver {

    private const val FOLDER = "Metamorph"

    /**
     * يحفظ الصورة في معرض الجهاز داخل مجلد Metamorph.
     * يعيد true عند النجاح.
     */
    fun saveImage(ctx: Context, bitmap: Bitmap): Boolean {
        val fileName = "FB_${System.currentTimeMillis()}.jpg"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(ctx, bitmap, fileName)
            } else {
                saveViaFile(bitmap, fileName)
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun saveViaMediaStore(
        ctx: Context,
        bitmap: Bitmap,
        fileName: String
    ): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$FOLDER"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = ctx.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return false

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        } ?: return false

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return true
    }

    private fun saveViaFile(bitmap: Bitmap, fileName: String): Boolean {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            ),
            FOLDER
        )
        if (!dir.exists()) dir.mkdirs()

        FileOutputStream(File(dir, fileName)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return true
    }
}
