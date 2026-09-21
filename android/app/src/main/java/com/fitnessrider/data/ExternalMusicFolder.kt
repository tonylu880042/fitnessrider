package com.fitnessrider.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Layer 3「資料夾記憶」：對應舊版 `MusicUtility.getMusicPath()` / `isIncludeSubdirectories()`，
 * 記住教練用 `OpenDocumentTree()` 選定的音樂資料夾與「含子資料夾」開關。
 *
 * 只用 SharedPreferences 存 tree Uri 字串與一個 boolean，沒有新增資料庫欄位／表格，
 * 也沒有把資料夾內容複製進來——實際內容一律用 [listExternalMusicEntries] 即時查詢。
 */
object ExternalMusicFolderPrefs {
    private const val PREFS_NAME = "external_music_folder"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_INCLUDE_SUBDIRS = "include_subdirectories"

    fun getFolderUri(context: Context): Uri? =
        prefs(context).getString(KEY_TREE_URI, null)?.let { Uri.parse(it) }

    fun setFolderUri(context: Context, uri: Uri?) {
        prefs(context).edit().apply {
            if (uri == null) remove(KEY_TREE_URI) else putString(KEY_TREE_URI, uri.toString())
        }.apply()
    }

    // 舊版 MusicUtility.isIncludeSubdirectories 預設值就是 true，這裡沿用。
    fun isIncludeSubdirectories(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_SUBDIRS, true)

    fun setIncludeSubdirectories(context: Context, include: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_SUBDIRS, include).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * 資料夾內一筆音樂檔的串流列表項目：只帶位址與檔名，時長/BPM 留到教練實際選取後才分析快取。
 *
 * [documentUriString] 刻意存成 String 而非 [Uri]：純 JVM 單元測試無法建立真的 `Uri` 實例
 * （`Uri.parse` 在測試環境是未實作的 stub，會直接丟例外），存字串可以讓篩選、建立段落這些
 * 非顯而易見的邏輯維持可測試，實際播放/查詢時才在呼叫端用 `Uri.parse` 還原。
 */
data class ExternalMusicEntry(
    val documentUriString: String,
    val displayName: String
)

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "wma")

/** 依副檔名或 MIME type 判斷是否為音樂檔，用來在資料夾掃描時過濾非音樂項目。 */
internal fun isAudioDocument(displayName: String, mimeType: String?): Boolean {
    if (mimeType != null && mimeType.startsWith("audio/")) return true
    val ext = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return ext in AUDIO_EXTENSIONS
}

/**
 * 串流列出 [treeUri] 資料夾內容：透過 `DocumentsContract` 對 child documents 做 Cursor 查詢，
 * 一次只取檔名/MIME 等中繼資料，不開啟、不下載、更不複製任何音樂檔案本體
 * （對應 CLAUDE.md Layer 3「以串流方式列出，不要一次把整個資料夾複製進 app 儲存空間」）。
 *
 * [includeSubdirectories] 開啟時遞迴掃描子資料夾，對應舊版「含子資料夾」選項。
 *
 * 資料夾授權被使用者撤銷、或資料夾本身已經被移除時，`contentResolver.query` 會丟
 * [SecurityException]／回傳 null，這裡不吞掉例外，讓呼叫端（畫面層）決定要顯示
 * 「資料夾存取已失效，請重新選擇」之類的訊息。
 */
fun listExternalMusicEntries(
    context: Context,
    treeUri: Uri,
    includeSubdirectories: Boolean
): List<ExternalMusicEntry> {
    val result = mutableListOf<ExternalMusicEntry>()

    fun scan(documentId: String) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (includeSubdirectories) scan(docId)
                } else if (isAudioDocument(name, mime)) {
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    result.add(ExternalMusicEntry(documentUri.toString(), name))
                }
            }
        }
    }

    scan(DocumentsContract.getTreeDocumentId(treeUri))
    return result
}
