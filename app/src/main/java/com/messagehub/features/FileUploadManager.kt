package com.messagehub.features

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.messagehub.data.Attachment
import com.messagehub.data.AttachmentType
import com.messagehub.network.FileUploadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileUploadService: FileUploadService
) {
    
    companion object {
        private const val TAG = "FileUploadManager"
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB
    }
    
    suspend fun uploadFile(uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        try {
            val file = uriToFile(uri) ?: return@withContext null
            
            if (file.length() > MAX_FILE_SIZE) {
                Log.e(TAG, "File too large: ${file.length()} bytes")
                return@withContext null
            }
            
            val mimeType = getMimeType(uri)
            val attachmentType = getAttachmentType(mimeType)
            
            val requestFile = file.asRequestBody(mimeType?.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            
            val response = fileUploadService.uploadFile(body)
            
            Attachment(
                id = response.fileId,
                type = attachmentType,
                url = response.url,
                fileName = file.name,
                fileSize = file.length(),
                mimeType = mimeType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file", e)
            null
        }
    }
    
    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = getFileName(uri) ?: "temp_file"
            val tempFile = File(context.cacheDir, fileName)
            
            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert URI to file", e)
            null
        }
    }
    
    private fun getFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    }
    
    private fun getMimeType(uri: Uri): String? {
        return context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            )
    }
    
    private fun getAttachmentType(mimeType: String?): AttachmentType {
        return when {
            mimeType?.startsWith("image/") == true -> AttachmentType.IMAGE
            mimeType?.startsWith("video/") == true -> AttachmentType.VIDEO
            mimeType?.startsWith("audio/") == true -> AttachmentType.AUDIO
            else -> AttachmentType.DOCUMENT
        }
    }
}

data class FileUploadResponse(
    val fileId: String,
    val url: String,
    val fileName: String,
    val fileSize: Long
)
