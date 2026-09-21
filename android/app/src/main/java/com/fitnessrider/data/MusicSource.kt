package com.fitnessrider.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * 統一處理兩種音樂來源，讓播放／波形分析／匯出等消費端不用各自判斷：
 * - Layer 2：複製進 `filesDir/Music` 的檔名（純檔名字串）。
 * - Layer 3：教練指定的外部資料夾內容，一律以該檔案的 SAF content Uri 字串儲存在
 *   `WorkoutSegment.musicFileName`，天然以 "content://" 開頭，不需要另外加欄位標記來源。
 *
 * 外部資料夾的授權可能被使用者事後撤銷、或檔案被搬走/刪除，[exists] 一律用 try/catch
 * 吞掉例外並回傳 false，呼叫端視同「找不到檔案」處理（與 Layer 1/2 既有的容錯行為一致）。
 */
object MusicSource {
    private const val EXTERNAL_URI_PREFIX = "content://"

    fun isExternalUri(musicFileName: String): Boolean = musicFileName.startsWith(EXTERNAL_URI_PREFIX)

    fun resolveUri(repository: ClassRepository, musicFileName: String): Uri =
        if (isExternalUri(musicFileName)) {
            Uri.parse(musicFileName)
        } else {
            Uri.fromFile(File(repository.musicDirectory, musicFileName))
        }

    fun exists(context: Context, repository: ClassRepository, musicFileName: String): Boolean {
        if (musicFileName.isBlank()) return false
        return if (isExternalUri(musicFileName)) {
            try {
                context.contentResolver.openFileDescriptor(Uri.parse(musicFileName), "r")?.use { true } ?: false
            } catch (e: Exception) {
                false
            }
        } else {
            File(repository.musicDirectory, musicFileName).exists()
        }
    }

    fun openInputStream(context: Context, repository: ClassRepository, musicFileName: String): InputStream? {
        if (musicFileName.isBlank()) return null
        return try {
            if (isExternalUri(musicFileName)) {
                context.contentResolver.openInputStream(Uri.parse(musicFileName))
            } else {
                val file = File(repository.musicDirectory, musicFileName)
                if (file.exists()) file.inputStream() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
