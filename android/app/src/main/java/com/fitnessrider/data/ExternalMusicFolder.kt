package com.fitnessrider.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

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

    fun isIncludeSubdirectories(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_SUBDIRS, true)

    fun setIncludeSubdirectories(context: Context, include: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_SUBDIRS, include).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

data class ExternalMusicEntry(
    val documentUriString: String,
    val displayName: String
)

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "wma")

internal fun isAudioDocument(displayName: String, mimeType: String?): Boolean {
    if (mimeType != null && mimeType.startsWith("audio/")) return true
    val ext = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return ext in AUDIO_EXTENSIONS
}

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
