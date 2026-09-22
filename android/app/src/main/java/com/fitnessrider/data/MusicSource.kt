package com.fitnessrider.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

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
