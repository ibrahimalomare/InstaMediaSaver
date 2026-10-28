package com.example.instamediasaver.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shortcode: String,
    val itemIndex: Int,
    val sourcePostUrl: String,
    val authorUsername: String?,
    val mediaKind: String, // "IMAGE" or "VIDEO"
    val mimeType: String,
    val fileName: String,
    val localContentUri: String,
    val thumbnailUrl: String?,
    val fileSizeBytes: Long,
    val downloadedAtEpochMs: Long
)
