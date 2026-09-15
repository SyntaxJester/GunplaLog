package com.syntaxjester.gunplalog

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/**
 * 自定义拍照契约。
 *
 * 不复用 ActivityResultContracts.TakePicture，因为它不会给相机应用授予
 * EXTRA_OUTPUT 这个 URI 的写权限，部分定制系统（ColorOS / OneUI 等）会
 * 因此写不进文件而静默失败。这里补上 clipData + grant flags。
 */
class TakePictureGranting : ActivityResultContract<Uri, Boolean>() {

    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            clipData = ClipData.newRawUri("output", input)
            addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == android.app.Activity.RESULT_OK
}
