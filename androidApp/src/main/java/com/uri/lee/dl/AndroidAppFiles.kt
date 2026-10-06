package com.uri.lee.dl

import android.content.Context
import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.shared.training.LocalBackbones
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files under the app's private storage (filesDir/app). */
internal class AndroidAppFiles(private val root: File) : AppFiles {
    private fun file(name: String) = File(root, name)

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) { file(name).takeIf { it.isFile }?.readBytes() }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = file(name).apply { parentFile?.mkdirs() }
        // Written aside then moved, so a stop part-way leaves the old file, not half a new one
        val temporary = File(target.path + ".part")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(target)) { "Couldn't save $name" }
    }

    override suspend fun append(name: String, text: String) = withContext(Dispatchers.IO) {
        file(name).apply { parentFile?.mkdirs() }.appendText(text)
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { file(name).deleteRecursively() }
    }

    override suspend fun list(folder: String): List<String> = withContext(Dispatchers.IO) { file(folder).list()?.sorted().orEmpty() }

    override suspend fun location(name: String): String = file(name).path
}

/** Backbones copied by adb into Android/data/com.uri.lee.dl/files/backbones (developers; see docs/user-trained-models.md). */
internal class AndroidLocalBackbones(private val context: Context) : LocalBackbones {
    override suspend fun locations(): Map<String, String> = withContext(Dispatchers.IO) {
        val folder = File(context.getExternalFilesDir(null) ?: return@withContext emptyMap(), "backbones")
        folder.listFiles { f -> f.extension == "tflite" }.orEmpty().associate { it.nameWithoutExtension to it.path }
    }

    override suspend fun read(location: String): ByteArray = withContext(Dispatchers.IO) { File(location).readBytes() }
}
