package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxy_profiles ORDER BY createdAt DESC")
    fun observeProfiles(): Flow<List<ProxyProfileEntity>>

    @Query("SELECT * FROM proxy_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): ProxyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProxyProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProxyProfileEntity)

    @Query("DELETE FROM proxy_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("UPDATE proxy_profiles SET lastPingMs = :pingMs, lastTestError = :error WHERE id = :id")
    suspend fun updatePingResult(id: Long, pingMs: Int?, error: String?)

    @Query("SELECT * FROM per_app_rules ORDER BY appLabel ASC")
    fun observePerAppRules(): Flow<List<PerAppRuleEntity>>

    @Query("SELECT packageName FROM per_app_rules")
    suspend fun getSelectedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerAppRule(rule: PerAppRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerAppRules(rules: List<PerAppRuleEntity>)

    @Query("DELETE FROM per_app_rules WHERE packageName = :packageName")
    suspend fun deletePerAppRule(packageName: String)

    @Query("DELETE FROM per_app_rules")
    suspend fun clearPerAppRules()

    @Transaction
    suspend fun replacePerAppRules(rules: List<PerAppRuleEntity>) {
        clearPerAppRules()
        if (rules.isNotEmpty()) {
            insertPerAppRules(rules)
        }
    }
}
