package com.kunalkcube.zipfont.processor

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.security.auth.x500.X500Principal

object ApkProcessor {

    private const val SKELETON_ASSET = "skeleton.zip"
    private const val RUNTIME_SIGN_ALIAS = "zipfont_runtime_signer"
    private const val RUNTIME_SIGN_SUBJECT = "CN=ZipFont Runtime, O=kunalkcube"
    private const val FONT_ENTRY_NAME = "assets/fonts/Samsungsans.ttf"
    private const val STORED_METHOD = 0
    private const val OUTPUT_FILE_NAME = "final_font.apk"
    private const val OUTPUT_FOLDER_NAME = "ZipFont"
    private const val MAX_FONT_BYTES = 20L * 1024L * 1024L
    private val OUTPUT_RELATIVE_DIRECTORY = "${Environment.DIRECTORY_DOWNLOADS}/$OUTPUT_FOLDER_NAME"

    const val OUTPUT_DISPLAY_PATH = "Downloads/$OUTPUT_FOLDER_NAME/$OUTPUT_FILE_NAME"

    sealed class ProcessState {
        object Idle : ProcessState()
        object ExtractingSkeleton : ProcessState()
        object CopyingFont : ProcessState()
        object InjectingFont : ProcessState()
        object ZipAligning : ProcessState()
        object SigningApk : ProcessState()
        object Ready : ProcessState()
        data class Error(val message: String) : ProcessState()
    }

    suspend fun processFont(
        context: Context,
        fontUri: android.net.Uri,
        onStateChange: (ProcessState) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.cacheDir
            val workingApk = File(cacheDir, "working.apk")
            val fontFile = File(cacheDir, "SamsungSans.ttf")
            val alignedApk = File(cacheDir, "aligned.apk")
            val finalApk = File(cacheDir, "final_font.apk")

            cleanPreviousOutputs(listOf(workingApk, fontFile, alignedApk, finalApk))

            onStateChange(ProcessState.ExtractingSkeleton)
            copySkeletonFromAssets(context, workingApk)

            onStateChange(ProcessState.CopyingFont)
            copyFontFromUri(context, fontUri, fontFile)

            onStateChange(ProcessState.InjectingFont)
            injectFontIntoApk(workingApk, fontFile)

            onStateChange(ProcessState.ZipAligning)
            ensureResourcesArscUncompressed(workingApk)
            zipAlignApk(workingApk, alignedApk)

            onStateChange(ProcessState.SigningApk)
            signApk(alignedApk, finalApk)
            saveApkToDownloads(context, finalApk)

            onStateChange(ProcessState.Ready)
            finalApk
        } catch (e: Exception) {
            onStateChange(ProcessState.Error(e.message ?: "Unknown error occurred"))
            null
        }
    }

    private fun cleanPreviousOutputs(files: List<File>) {
        files.forEach {
            if (it.exists() && !it.delete()) {
                throw IllegalStateException("Unable to delete stale file: ${it.absolutePath}")
            }
        }
    }

    private fun copySkeletonFromAssets(context: Context, dest: File) {
        context.assets.open(SKELETON_ASSET).use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
        ZipFile(dest).use { zip ->
            if (!zip.isValidZipFile) {
                throw IllegalStateException("skeleton.zip is not a valid ZIP archive")
            }
        }
    }

    private fun copyFontFromUri(context: Context, uri: android.net.Uri, dest: File) {
        val displayName = readDisplayName(context, uri)
        if (displayName != null) {
            val lower = displayName.lowercase()
            require(lower.endsWith(".ttf") || lower.endsWith(".otf")) {
                "Only .ttf or .otf fonts are supported"
            }
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(8192)
                var totalBytes = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > MAX_FONT_BYTES) {
                        throw IllegalArgumentException("Font is too large (max 20 MB)")
                    }
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()

                require(totalBytes > 0) { "Selected font file is empty" }
            }
        } ?: throw IllegalStateException("Could not open font URI")

        validateFontHeader(dest)
    }

    private fun readDisplayName(context: Context, uri: android.net.Uri): String? {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    private fun validateFontHeader(fontFile: File) {
        val header = ByteArray(4)
        val expectedTtf = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        val expectedOtf = byteArrayOf(0x4F, 0x54, 0x54, 0x4F) // OTTO

        FileInputStream(fontFile).use { input ->
            val read = input.read(header)
            require(read == 4) { "Invalid font file" }
        }

        val valid = header.contentEquals(expectedTtf) || header.contentEquals(expectedOtf)
        require(valid) { "Unsupported or corrupt font file" }
    }

    private fun injectFontIntoApk(apkFile: File, fontFile: File) {
        ZipFile(apkFile).use { zipFile ->
            if (zipFile.getFileHeader(FONT_ENTRY_NAME) != null) {
                zipFile.removeFile(FONT_ENTRY_NAME)
            }

            val params = ZipParameters().apply {
                compressionMethod = CompressionMethod.STORE
                fileNameInZip = FONT_ENTRY_NAME
            }

            zipFile.addFile(fontFile, params)
        }
    }

    private fun ensureResourcesArscUncompressed(apkFile: File) {
        val tempDir = File(apkFile.parentFile, "arsc-temp")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val extractedFile = File(tempDir, "resources.arsc")

        try {
            ZipFile(apkFile).use { zipFile ->
                val header = zipFile.getFileHeader("resources.arsc")
                    ?: throw IllegalStateException("resources.arsc not found in skeleton APK")

                if (header.compressionMethod == CompressionMethod.STORE) {
                    return
                }

                if (extractedFile.exists()) {
                    extractedFile.delete()
                }

                zipFile.extractFile("resources.arsc", tempDir.absolutePath, extractedFile.name)
                zipFile.removeFile("resources.arsc")

                val params = ZipParameters().apply {
                    compressionMethod = CompressionMethod.STORE
                    fileNameInZip = "resources.arsc"
                }
                zipFile.addFile(extractedFile, params)
            }
        } finally {
            if (extractedFile.exists()) {
                extractedFile.delete()
            }
            if (tempDir.exists()) {
                tempDir.delete()
            }
        }
    }

    private fun zipAlignApk(inputApk: File, outputApk: File) {
        val inputBytes = inputApk.readBytes()
        val eocd = findEocd(inputBytes)
            ?: throw IllegalStateException("End Of Central Directory not found")
        val entries = parseCentralDirectory(inputBytes, eocd)
            .sortedBy { it.localHeaderOffset }

        val resourcesEntry = entries.firstOrNull { it.fileName == "resources.arsc" }
            ?: throw IllegalStateException("resources.arsc not found in skeleton APK")
        require(resourcesEntry.compressionMethod == STORED_METHOD) {
            "resources.arsc must remain uncompressed"
        }

        val oldToNewLocalOffsets = HashMap<Int, Int>(entries.size)

        FileOutputStream(outputApk).use { output ->
            var outputOffset = 0
            entries.forEachIndexed { index, entry ->
                val nextEntryOffset = if (index < entries.lastIndex) {
                    entries[index + 1].localHeaderOffset
                } else {
                    eocd.centralDirectoryOffset
                }

                val totalEntryLength = nextEntryOffset - entry.localHeaderOffset
                require(totalEntryLength > 0) { "Invalid ZIP local entry boundaries" }

                val localHeader = parseLocalHeader(inputBytes, entry.localHeaderOffset)
                val localHeaderLength = 30 + localHeader.fileNameLength + localHeader.extraFieldLength
                require(localHeaderLength <= totalEntryLength) { "Invalid local file header size" }

                val needsAlignment = entry.compressionMethod == STORED_METHOD
                val padding = if (needsAlignment) {
                    val dataStart = outputOffset + localHeaderLength
                    (4 - (dataStart % 4)) % 4
                } else {
                    0
                }

                val oldHeaderStart = entry.localHeaderOffset
                val nameStart = oldHeaderStart + 30
                val oldExtraStart = nameStart + localHeader.fileNameLength
                val payloadStart = oldExtraStart + localHeader.extraFieldLength
                val payloadLength = totalEntryLength - localHeaderLength

                val newExtraLength = localHeader.extraFieldLength + padding
                require(newExtraLength <= 0xFFFF) { "ZIP extra field too large" }

                val fixedHeaderPrefix = inputBytes.copyOfRange(oldHeaderStart, oldHeaderStart + 30)
                writeUInt16LE(fixedHeaderPrefix, 28, newExtraLength)

                oldToNewLocalOffsets[entry.localHeaderOffset] = outputOffset

                output.write(fixedHeaderPrefix)
                output.write(inputBytes, nameStart, localHeader.fileNameLength)
                output.write(inputBytes, oldExtraStart, localHeader.extraFieldLength)
                if (padding > 0) {
                    output.write(ByteArray(padding))
                }
                output.write(inputBytes, payloadStart, payloadLength)

                outputOffset += 30 + localHeader.fileNameLength + newExtraLength + payloadLength
            }

            val centralDirectory = inputBytes.copyOfRange(
                eocd.centralDirectoryOffset,
                eocd.centralDirectoryOffset + eocd.centralDirectorySize
            )
            patchCentralDirectoryOffsets(centralDirectory, oldToNewLocalOffsets)
            val newCdOffset = outputOffset

            output.write(centralDirectory)
            outputOffset += centralDirectory.size

            val eocdBytes = inputBytes.copyOfRange(eocd.eocdOffset, inputBytes.size)
            writeUInt32LE(eocdBytes, 12, centralDirectory.size)
            writeUInt32LE(eocdBytes, 16, newCdOffset)
            output.write(eocdBytes)
        }
    }

    private fun signApk(inputApk: File, outputApk: File) {
        val (privateKey, certificate) = getOrCreateRuntimeSigningMaterial()
        val signerConfig = ApkSigner.SignerConfig.Builder(
            RUNTIME_SIGN_ALIAS,
            privateKey,
            listOf(certificate)
        ).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    private fun getOrCreateRuntimeSigningMaterial(): Pair<PrivateKey, X509Certificate> {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        if (!keyStore.containsAlias(RUNTIME_SIGN_ALIAS)) {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )

            val now = Calendar.getInstance()
            val expiry = Calendar.getInstance().apply { add(Calendar.YEAR, 25) }

            val parameterSpec = KeyGenParameterSpec.Builder(
                RUNTIME_SIGN_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setCertificateSubject(X500Principal(RUNTIME_SIGN_SUBJECT))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(now.time)
                .setCertificateNotAfter(expiry.time)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .build()

            keyPairGenerator.initialize(parameterSpec)
            keyPairGenerator.generateKeyPair()
        }

        val privateKey = keyStore.getKey(RUNTIME_SIGN_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Unable to load runtime signing private key")
        val certificate = keyStore.getCertificate(RUNTIME_SIGN_ALIAS) as? X509Certificate
            ?: throw IllegalStateException("Unable to load runtime signing certificate")

        return privateKey to certificate
    }

    private fun saveApkToDownloads(context: Context, apkFile: File) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val displayName = OUTPUT_FILE_NAME
        val relativePath = OUTPUT_RELATIVE_DIRECTORY

        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(displayName, "$relativePath/"),
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val existingUri = android.content.ContentUris.withAppendedId(collection, id)
                resolver.delete(existingUri, null, null)
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Unable to create Downloads/ZipFont/final_font.apk")

        try {
            resolver.openOutputStream(itemUri, "w")?.use { output ->
                FileInputStream(apkFile).use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to open output stream for saved APK")

            val complete = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(itemUri, complete, null, null)
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }
    }

    private data class Eocd(
        val eocdOffset: Int,
        val centralDirectoryOffset: Int,
        val centralDirectorySize: Int
    )

    private data class CentralDirectoryEntry(
        val compressionMethod: Int,
        val fileName: String,
        val localHeaderOffset: Int
    )

    private data class LocalHeader(
        val fileNameLength: Int,
        val extraFieldLength: Int
    )

    private fun parseLocalHeader(bytes: ByteArray, offset: Int): LocalHeader {
        if (readUInt32LE(bytes, offset) != 0x04034b50) {
            throw IllegalStateException("Invalid local file header signature")
        }
        return LocalHeader(
            fileNameLength = readUInt16LE(bytes, offset + 26),
            extraFieldLength = readUInt16LE(bytes, offset + 28)
        )
    }

    private fun parseCentralDirectory(bytes: ByteArray, eocd: Eocd): List<CentralDirectoryEntry> {
        val result = mutableListOf<CentralDirectoryEntry>()
        var offset = eocd.centralDirectoryOffset
        val end = eocd.centralDirectoryOffset + eocd.centralDirectorySize

        while (offset + 46 <= end) {
            if (readUInt32LE(bytes, offset) != 0x02014b50) {
                break
            }

            val compressionMethod = readUInt16LE(bytes, offset + 10)
            val fileNameLength = readUInt16LE(bytes, offset + 28)
            val extraLength = readUInt16LE(bytes, offset + 30)
            val commentLength = readUInt16LE(bytes, offset + 32)
            val localHeaderOffset = readUInt32LE(bytes, offset + 42)
            val fileName = String(bytes, offset + 46, fileNameLength, Charsets.UTF_8)

            result += CentralDirectoryEntry(
                compressionMethod = compressionMethod,
                fileName = fileName,
                localHeaderOffset = localHeaderOffset
            )

            offset += 46 + fileNameLength + extraLength + commentLength
        }

        if (result.isEmpty()) {
            throw IllegalStateException("No entries in ZIP central directory")
        }

        return result
    }

    private fun patchCentralDirectoryOffsets(
        centralDirectory: ByteArray,
        oldToNewLocalOffsets: Map<Int, Int>
    ) {
        var offset = 0
        while (offset + 46 <= centralDirectory.size) {
            if (readUInt32LE(centralDirectory, offset) != 0x02014b50) {
                break
            }

            val oldLocalOffset = readUInt32LE(centralDirectory, offset + 42)
            val newLocalOffset = oldToNewLocalOffsets[oldLocalOffset] ?: oldLocalOffset
            writeUInt32LE(centralDirectory, offset + 42, newLocalOffset)

            val fileNameLength = readUInt16LE(centralDirectory, offset + 28)
            val extraLength = readUInt16LE(centralDirectory, offset + 30)
            val commentLength = readUInt16LE(centralDirectory, offset + 32)
            offset += 46 + fileNameLength + extraLength + commentLength
        }
    }

    private fun findEocd(bytes: ByteArray): Eocd? {
        val minOffset = (bytes.size - 65557).coerceAtLeast(0)
        for (offset in bytes.size - 22 downTo minOffset) {
            if (readUInt32LE(bytes, offset) == 0x06054b50) {
                return Eocd(
                    eocdOffset = offset,
                    centralDirectorySize = readUInt32LE(bytes, offset + 12),
                    centralDirectoryOffset = readUInt32LE(bytes, offset + 16)
                )
            }
        }
        return null
    }

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(bytes, offset, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
            .toInt() and 0xFFFF
    }

    private fun readUInt32LE(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(bytes, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
    }

    private fun writeUInt16LE(bytes: ByteArray, offset: Int, value: Int) {
        val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
        val data = bb.array()
        bytes[offset] = data[0]
        bytes[offset + 1] = data[1]
    }

    private fun writeUInt32LE(bytes: ByteArray, offset: Int, value: Int) {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
        val data = bb.array()
        bytes[offset] = data[0]
        bytes[offset + 1] = data[1]
        bytes[offset + 2] = data[2]
        bytes[offset + 3] = data[3]
    }
}
