package app.brokoli5191.zeitig.core.search

import java.text.Normalizer

/**
 * Normalisation shared by the stop index and the query, so "Hütteldorfer Str." finds
 * "Huetteldorfer Strasse" and "hutteldorfer straße" alike. Both sides go through the same
 * transformation, so aggressive folding (ae → a) cannot cause false negatives.
 */
public object SearchText {
    private val combiningMarks = Regex("""\p{Mn}+""")
    private val nonAlphanumeric = Regex("""[^\p{L}\p{N}]+""")
    private val whitespace = Regex("""\s+""")

    // Order matters: dotted abbreviations first, then compound tails. "str"/"pl" without a dot are
    // only expanded when glued to a word ("Hauptstr"), so typing "str…" still prefix-matches
    // "Strebersdorf".
    private val abbreviations =
        listOf(
            Regex("""\bstr\.""") to "strasse",
            Regex("""(?<=\p{L})str\b\.?""") to "strasse",
            Regex("""\bpl\.""") to "platz",
            Regex("""(?<=\p{L})pl\.""") to "platz",
            Regex("""(?<=\p{L})g\.""") to "gasse",
            Regex("""\bst\.""") to "sankt",
            Regex("""\bhbf\b\.?""") to "hauptbahnhof",
            Regex("""\bbhf\b\.?""") to "bahnhof",
        )

    /** Lower-case, umlauts folded (ä/ae → a, ö/oe → o, ü/ue → u, ß → ss), punctuation to spaces. */
    public fun normalize(input: String): String {
        var s = input.lowercase().replace("ß", "ss")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(combiningMarks, "")
        s = s.replace("ae", "a").replace("oe", "o").replace("ue", "u")
        for ((regex, replacement) in abbreviations) s = regex.replace(s, replacement)
        s = s.replace(nonAlphanumeric, " ").trim()
        return s.replace(whitespace, " ")
    }

    public fun tokens(normalized: String): List<String> = if (normalized.isEmpty()) emptyList() else normalized.split(' ')
}
