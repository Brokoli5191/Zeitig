package app.brokoli5191.zeitig.data

import java.io.File

internal object Fixtures {
    fun text(name: String): String =
        requireNotNull(Fixtures::class.java.classLoader!!.getResource("fixtures/$name")) { "missing fixture $name" }
            .readText(Charsets.UTF_8)

    /** The bundled snapshot; unit tests run with the module directory as working directory. */
    val bundledSnapshot: File = File("src/main/assets/ogd")
}
