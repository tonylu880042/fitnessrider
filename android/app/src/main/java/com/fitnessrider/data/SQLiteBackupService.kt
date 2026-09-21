package com.fitnessrider.data

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SQLiteBackupService(private val context: Context) {

    fun exportBackupFile(): File? {
        return try {
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists()) return null

            // Force Room checkpoint by closing DB temporarily if needed, or copy current file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val backupFileName = "FitnessRider_Backup_$timestamp.sqlite"
            val exportDir = File(context.cacheDir, "backups")
            if (!exportDir.exists()) exportDir.mkdirs()

            val exportFile = File(exportDir, backupFileName)
            copyFile(dbFile, exportFile)
            exportFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun restoreFromFile(sourceFile: File): Boolean {
        return try {
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            copyFile(sourceFile, dbFile)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output)
            }
        }
    }
}
