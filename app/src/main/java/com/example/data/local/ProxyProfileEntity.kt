package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ProxyProtocol

@Entity(tableName = "proxy_profiles")
data class ProxyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val lastPingMs: Int? = null,
    val lastTestError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val hasAuth: Boolean get() = username.isNotBlank()
    val endpoint: String get() = "$host:$port"
}
