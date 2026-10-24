package com.ninjavault.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_files WHERE folderPath = :folderPath ORDER BY displayName ASC")
    fun observeFolder(folderPath: String): Flow<List<VaultFileEntity>>

    /** [query] must already have `\`, `%` and `_` escaped with a backslash (see VaultRepository). */
    @Query("SELECT * FROM vault_files WHERE displayName LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY displayName ASC")
    fun search(query: String): Flow<List<VaultFileEntity>>

    @Query("DELETE FROM vault_files")
    suspend fun deleteAll()

    @Query("SELECT * FROM vault_files WHERE id = :id")
    suspend fun getById(id: Long): VaultFileEntity?

    @Insert
    suspend fun insert(file: VaultFileEntity): Long

    @Update
    suspend fun update(file: VaultFileEntity)

    @Delete
    suspend fun delete(file: VaultFileEntity)

    @Query("SELECT DISTINCT folderPath FROM vault_files")
    fun observeFolders(): Flow<List<String>>
}
