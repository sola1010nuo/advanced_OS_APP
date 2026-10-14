package com.example.petcollection.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validates the authored, localized catalog without requiring an emulator. */
class BreedIntroductionResourcesTest {
    private val resourceRoot = File("src/main/res").takeIf { it.isDirectory }
        ?: File("app/src/main/res")
    private val locales = listOf("values", "values-b+zh+Hant", "values-en")
    private val expectedKeys = (MockBreedRepository.normalBreedIds + MockBreedRepository.legacyBreedIds - MockBreedRepository.MONKEY_ID)
        .map { "breed_${it}_introduction" }.toSet()

    @Test fun everyCatAndDogHasAnIndependentIntroductionInEveryLocale() {
        assertEquals(64, expectedKeys.size)
        locales.forEach { locale ->
            val introductions = introductions(locale)
            assertEquals("Introduction coverage in $locale", expectedKeys, introductions.keys)
            assertEquals("Introductions must be distinct in $locale", 64, introductions.values.toSet().size)
            introductions.forEach { (key, text) ->
                assertTrue("Blank introduction: $locale/$key", text.isNotBlank())
                assertTrue("Template placeholder: $locale/$key", !text.contains(Regex("%[0-9$]*[sd]")))
            }
        }
    }

    @Test fun defaultIntroductionsMatchTraditionalChinese() {
        assertEquals(introductions("values-b+zh+Hant"), introductions("values"))
    }

    @Test fun introductionsStayWithinTheRequestedReadingLength() {
        introductions("values-b+zh+Hant").forEach { (key, text) ->
            assertTrue("Chinese length ${text.length}: $key", text.length in 80..120)
            val chineseCharacters = text.count { it in '\u3400'..'\u9fff' }
            assertTrue("Too little Chinese content: $key", chineseCharacters >= 80)
        }
        introductions("values-en").forEach { (key, text) ->
            val words = Regex("\\S+").findAll(text).count()
            assertTrue("English length $words: $key", words in 60..90)
        }
    }

    private fun introductions(locale: String): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(File(resourceRoot, "$locale/breed_catalog.xml"))
        val nodes = document.getElementsByTagName("string")
        val strings = (0 until nodes.length).map { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
        assertEquals("Duplicate resource keys in $locale", strings.size, strings.map { it.first }.toSet().size)
        return strings.filter { (key, _) -> key.endsWith("_introduction") }.toMap()
    }
}
