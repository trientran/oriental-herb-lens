package com.uri.lee.dl.testing.fakes

import com.uri.lee.dl.domain.training.AppFiles

/** The app's files, in memory. */
class MemoryAppFiles : AppFiles {
    val files = mutableMapOf<String, ByteArray>()
    override suspend fun read(name: String) = files[name]
    override suspend fun write(name: String, bytes: ByteArray) { files[name] = bytes }
    override suspend fun append(name: String, text: String) { files[name] = (files[name] ?: ByteArray(0)) + text.encodeToByteArray() }
    override suspend fun delete(name: String) { files.keys.removeAll { it == name || it.startsWith("$name/") } }
    override suspend fun list(folder: String) =
        files.keys.filter { it.startsWith("$folder/") }.map { it.removePrefix("$folder/").substringBefore('/') }.distinct().sorted()
    override suspend fun location(name: String) = name
}
