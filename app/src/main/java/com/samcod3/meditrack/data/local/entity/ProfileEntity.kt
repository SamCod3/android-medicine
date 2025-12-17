package com.samcod3.meditrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarColor: Long, // Color int
    val createdAt: Long = System.currentTimeMillis()
)
