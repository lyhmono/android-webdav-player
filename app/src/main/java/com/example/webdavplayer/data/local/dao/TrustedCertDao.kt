package com.example.webdavplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.webdavplayer.data.local.entity.TrustedCertEntity
import kotlinx.coroutines.flow.Flow

/** 已信任自签证书 DAO（§1.4）。 */
@Dao
interface TrustedCertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cert: TrustedCertEntity)

    @Query("SELECT * FROM trusted_certs WHERE serverId = :serverId")
    suspend fun getByServer(serverId: String): List<TrustedCertEntity>

    @Query("SELECT sha256Fingerprint FROM trusted_certs WHERE serverId = :serverId")
    suspend fun getFingerprints(serverId: String): List<String>

    @Query("SELECT * FROM trusted_certs")
    fun observeAll(): Flow<List<TrustedCertEntity>>

    @Query("DELETE FROM trusted_certs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM trusted_certs WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)
}
