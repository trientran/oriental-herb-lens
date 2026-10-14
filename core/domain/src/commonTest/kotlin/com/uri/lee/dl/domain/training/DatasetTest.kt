package com.uri.lee.dl.domain.training

import com.uri.lee.dl.domain.media.LocalImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun stageFoldersSetTheOrderClassesArriveIn() {
        val d = dataset(
            "phone/10 Later/Cyperus iria/1.jpg",
            "phone/2 Australia/Hedera hibernica/1.jpg",
            "phone/1 Vietnam/Mimosa pigra/1.jpg", "phone/1 Vietnam/Lantana camara/1.jpg",
        )

        assertEquals(listOf("1 Vietnam", "2 Australia", "10 Later"), d.stages)
        assertEquals(listOf("Cyperus iria", "Hedera hibernica", "Lantana camara", "Mimosa pigra"), d.classes)
        assertEquals(0, d.stageOf("Mimosa pigra"))
        assertEquals(2, d.stageOf("Cyperus iria"))
    }

    @Test
    fun noStagesWithoutTwoStageFolders() {
        assertNull(dataset("weeds/Lantana camara/1.jpg", "weeds/Mimosa pigra/1.jpg").stages)
        assertNull(dataset("Lantana camara/1.jpg", "Mimosa pigra/1.jpg").stages)
        // A class in two stages, or a photo outside any stage, gives no order
        assertNull(dataset("1 A/Lantana camara/1.jpg", "2 B/Lantana camara/2.jpg", "2 B/Mimosa pigra/1.jpg").stages)
        assertNull(dataset("1 A/Lantana camara/1.jpg", "2 B/Mimosa pigra/1.jpg", "Cyperus iria/1.jpg").stages)
    }
}
