package com.ninjavault.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VaultFileType { IMAGE, VIDEO, AUDIO, DOCUMENT, PDF, ZIP, OTHER }

/**
 * Metadata for one encrypted file in the vault. The actual bytes live encrypted
 * on disk at [encryptedPath]; [wrappedKeyBase64] is the per-file DEK, wrapped by
 * the Keystore master key (see CryptoManager). No plaintext content or key
 * material is ever stored in this table.
 */
@Entity(tableName = "vault_files")
data class VaultFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val folderPath: String,       // logical folder path within the vault, e.g. "/Photos"
    val encryptedPath: String,    // path to ciphertext blob in app-private storage
    val wrappedKeyBase64: String, // per-file DEK wrapped by the Keystore master key
    val mimeType: String,
    val fileType: VaultFileType,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long
)
