package com.uri.lee.dl.shared.research

import com.uri.lee.dl.domain.media.LocalImage
import kotlin.test.Test
import kotlin.test.assertEquals

class DatasetTest {

    private object Photo : LocalImage {
        override val uri = "test"
    }

    private fun dataset(vararg paths: String) = Dataset.fromPaths("test", paths.map { it to Photo })

    @Test
    fun classIsTheFolderAPhotoIsIn() {
        val d = dataset("Lantana camara/1.jpg", "Lantana camara/2.JPG", "Mimosa pigra/1.png")

        assertEquals(listOf("Lantana camara", "Mimosa pigra"), d.classes)
        assertEquals(3, d.images.size)
    }

    @Test
    fun looksThroughOneWrappingFolder() {
        val d = dataset("weeds/Lantana camara/1.jpg", "weeds/Mimosa pigra/1.jpg")

        assertEquals(listOf("Lantana camara", "Mimosa pigra"), d.classes)
    }

    @Test
    fun skipsOtherFilesHiddenOnesAndLoosePhotos() {
        val d = dataset(
            "Lantana camara/1.jpg", "Mimosa pigra/1.jpg",
            "Mimosa pigra/notes.txt", ".DS_Store", "Mimosa pigra/.hidden.jpg", "__MACOSX/Mimosa pigra/._1.jpg", "loose.jpg",
        )

        assertEquals(2, d.images.size)
    }
}
