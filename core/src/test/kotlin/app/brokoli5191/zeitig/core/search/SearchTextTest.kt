package app.brokoli5191.zeitig.core.search

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class SearchTextTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "Hütteldorfer Straße|hutteldorfer strasse",
            "Huetteldorfer Strasse|hutteldorfer strasse",
            "hütteldorfer str.|hutteldorfer strasse",
            "Hütteldorfer Str|hutteldorfer str",
            "Hauptstr.|hauptstrasse",
            "Lerchenfelder Gürtel|lerchenfelder gurtel",
            "Friedrich-Engels-Pl.|friedrich engels platz",
            "Hütteldorf, Bujattig.|hutteldorf bujattigasse",
            "St. Marx|sankt marx",
            "Wien Hbf|wien hauptbahnhof",
            "Schwedenplatz U|schwedenplatz u",
            "  Praterstern   S U  |praterstern s u",
            "Gänserndorf|ganserndorf",
            "Köhlergasse|kohlergasse",
            "Strebersdorf|strebersdorf",
            "Café Größenwahn|cafe grossenwahn",
        ],
    )
    fun normalizes(
        input: String,
        expected: String,
    ) {
        assertEquals(expected, SearchText.normalize(input))
    }

    @org.junit.jupiter.api.Test
    fun `tokens split on spaces and ignore empty input`() {
        assertEquals(listOf("karls", "platz"), SearchText.tokens("karls platz"))
        assertEquals(emptyList(), SearchText.tokens(""))
    }
}
