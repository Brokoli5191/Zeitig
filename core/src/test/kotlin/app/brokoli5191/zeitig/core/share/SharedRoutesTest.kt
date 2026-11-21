package app.brokoli5191.zeitig.core.share

import app.brokoli5191.zeitig.core.model.GeoPoint
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedRoutesTest {
    private val stephansplatz = GeoPoint(48.2085, 16.3731)
    private val schoenbrunn = GeoPoint(48.1845, 16.3122)
    private val westbahnhof = GeoPoint(48.1966, 16.3372)

    /** Shape of what maps.app.goo.gl redirects to today (place ids and coordinates per waypoint). */
    private val directions =
        "https://www.google.com/maps/dir/Stephansplatz/Schloss+Sch%C3%B6nbrunn/" +
            "data=!4m14!4m13!1m5!1m4!1s0x476d079f:0x2bd0a1e1!8m2!3d48.2085!4d16.3731" +
            "!1m5!1m4!1s0x476da81d:0x9c2f3b5d!8m2!3d48.1845!4d16.3122!3e3?utm_source=mstt_0&g_st=ac"

    @Test
    fun `current Google Maps directions give names and exact positions`() {
        val route = SharedRoutes.parse(directions)

        assertEquals(SharedRoute(SharedPlace("Stephansplatz", stephansplatz), SharedPlace("Schloss Schönbrunn", schoenbrunn)), route)
    }

    @Test
    fun `the link is found inside the text Google Maps shares`() {
        val text = "Route nach Schloss Schönbrunn: https://maps.app.goo.gl/AbCdEf123?g_st=ac."

        val link = SharedRoutes.findLink(text)

        assertEquals("https://maps.app.goo.gl/AbCdEf123?g_st=ac", link)
        assertTrue(SharedRoutes.isShortLink(link!!))
        assertNull(SharedRoutes.parse(text), "a short link must be expanded first")
    }

    @Test
    fun `short links are recognised by host`() {
        assertTrue(SharedRoutes.isShortLink("http://maps.app.goo.gl/xyz"))
        assertTrue(SharedRoutes.isShortLink("https://goo.gl/maps/xyz"))
        assertFalse(SharedRoutes.isShortLink("https://goo.gl/other"))
        assertFalse(SharedRoutes.isShortLink("ftp://maps.app.goo.gl/xyz"))
        assertFalse(SharedRoutes.isShortLink(directions))
        assertFalse(SharedRoutes.isShortLink("not a link"))
    }

    @Test
    fun `your location as start leaves the origin open`() {
        val link =
            "https://www.google.com/maps/dir//Westbahnhof,+1150+Wien/" +
                "data=!4m9!4m8!1m1!4e1!1m5!1m1!1s0x476d07:0x1a2b!2m2!1d16.3372!2d48.1966!3e3"

        val route = SharedRoutes.parse(link)

        assertEquals(SharedRoute(null, SharedPlace("Westbahnhof, 1150 Wien", westbahnhof)), route)
    }

    @Test
    fun `coordinates in the path and the older data format`() {
        val link =
            "https://www.google.com/maps/dir/48.2085,16.3731/Westbahnhof/@48.2,16.35,14z/" +
                "data=!4m8!4m7!1m0!1m5!1m1!1s0x476d07:0x1a2b!2m2!1d16.3372!2d48.1966!3e3/"

        val route = SharedRoutes.parse(link)

        assertEquals(SharedRoute(SharedPlace(null, stephansplatz), SharedPlace("Westbahnhof", westbahnhof)), route)
    }

    @Test
    fun `directions without a data block keep the names`() {
        val route = SharedRoutes.parse("https://www.google.at/maps/dir/Karlsplatz/Praterstern/")

        assertEquals(SharedRoute(SharedPlace("Karlsplatz", null), SharedPlace("Praterstern", null)), route)
    }

    @Test
    fun `a broken data block falls back to the path`() {
        val route = SharedRoutes.parse("https://www.google.com/maps/dir/Karlsplatz/48.1966,16.3372/data=!4m99!1m!xx!3dabc")

        assertEquals(SharedRoute(SharedPlace("Karlsplatz", null), SharedPlace(null, westbahnhof)), route)
    }

    @Test
    fun `directions need two waypoints`() {
        assertNull(SharedRoutes.parse("https://www.google.com/maps/dir/Karlsplatz/"))
        assertNull(SharedRoutes.parse("https://www.google.com/maps/dir//"))
    }

    @Test
    fun `Maps URLs with api=1`() {
        val dir =
            SharedRoutes.parse(
                "https://www.google.com/maps/dir/?api=1&origin=48.2085,16.3731&destination=Westbahnhof+Wien&travelmode=transit",
            )
        val search = SharedRoutes.parse("https://www.google.com/maps/search/?api=1&query=Prater")

        assertEquals(SharedRoute(SharedPlace(null, stephansplatz), SharedPlace("Westbahnhof Wien", null)), dir)
        assertEquals(SharedRoute(null, SharedPlace("Prater", null)), search)
        assertNull(SharedRoutes.parse("https://www.google.com/maps/dir/?api=1&origin=Karlsplatz"))
    }

    @Test
    fun `a shared place uses its own position, not the map view`() {
        val link =
            "https://www.google.com/maps/place/Riesenrad/@48.2170,16.3940,17z/" +
                "data=!3m1!4b1!4m6!3m5!1s0x476d0700:0x1234!8m2!3d48.2166!4d16.3964!16s%2Fm%2F02q2d?entry=ttu"

        assertEquals(SharedRoute(null, SharedPlace("Riesenrad", GeoPoint(48.2166, 16.3964))), SharedRoutes.parse(link))
        assertEquals(
            SharedRoute(null, SharedPlace("Riesenrad", null)),
            SharedRoutes.parse("https://www.google.com/maps/place/Riesenrad/@48.2170,16.3940,17z"),
        )
        assertNull(SharedRoutes.parse("https://www.google.com/maps/place/"))
    }

    @Test
    fun `searches and classic query links`() {
        assertEquals(
            SharedRoute(null, SharedPlace("Bäckerei", null)),
            SharedRoutes.parse("https://www.google.com/maps/search/B%C3%A4ckerei/@48.2,16.3,15z"),
        )
        assertEquals(SharedRoute(null, SharedPlace(null, stephansplatz)), SharedRoutes.parse("https://maps.google.com/?q=48.2085,16.3731"))
        assertEquals(
            SharedRoute(SharedPlace("Karlsplatz", null), SharedPlace(null, westbahnhof)),
            SharedRoutes.parse("https://maps.google.com/maps?saddr=Karlsplatz&daddr=48.1966,16.3372"),
        )
        assertNull(SharedRoutes.parse("https://www.google.com/maps/@48.2,16.3,15z"))
    }

    @Test
    fun `the EU consent redirect is unwrapped`() {
        val wrapped = "https://consent.google.com/ml?continue=" + URLEncoder.encode(directions, StandardCharsets.UTF_8) + "&gl=AT&hl=de"

        assertEquals(SharedRoutes.parse(directions), SharedRoutes.parse(wrapped))
        assertNull(SharedRoutes.parse("https://consent.google.com/ml?gl=AT"))
    }

    @Test
    fun `geo URIs`() {
        assertEquals(
            SharedRoute(null, SharedPlace("Stephansplatz", stephansplatz)),
            SharedRoutes.parse("geo:48.2085,16.3731?q=48.2085,16.3731(Stephansplatz)"),
        )
        assertEquals(SharedRoute(null, SharedPlace("Karlsplatz Wien", null)), SharedRoutes.parse("geo:0,0?q=Karlsplatz+Wien"))
        assertEquals(SharedRoute(null, SharedPlace(null, stephansplatz)), SharedRoutes.parse("geo:48.2085,16.3731;u=35"))
        assertEquals(SharedRoute(null, SharedPlace(null, stephansplatz)), SharedRoutes.parse("geo:0,0?q=48.2085,16.3731"))
        assertNull(SharedRoutes.parse("geo:0,0"))
        assertEquals("geo:48.2,16.3?q=48.2,16.3(Kiosk)", SharedRoutes.findLink("(siehe geo:48.2,16.3?q=48.2,16.3(Kiosk))!"))
    }

    @Test
    fun `Apple Maps links`() {
        assertEquals(
            SharedRoute(SharedPlace("Stephansplatz", null), SharedPlace(null, westbahnhof)),
            SharedRoutes.parse("https://maps.apple.com/?saddr=Stephansplatz&daddr=48.1966,16.3372&dirflg=r"),
        )
        assertEquals(
            SharedRoute(null, SharedPlace("Prater", stephansplatz)),
            SharedRoutes.parse("https://maps.apple.com/?q=Prater&ll=48.2085,16.3731"),
        )
        assertEquals(SharedRoute(null, SharedPlace(null, stephansplatz)), SharedRoutes.parse("https://maps.apple.com/?ll=48.2085,16.3731"))
        assertEquals(
            SharedRoute(null, SharedPlace("Mariahilfer Straße 1", null)),
            SharedRoutes.parse("https://maps.apple.com/?address=Mariahilfer%20Stra%C3%9Fe%201"),
        )
        assertNull(SharedRoutes.parse("https://maps.apple.com/?z=12"))
    }

    @Test
    fun `OpenStreetMap and OsmAnd links`() {
        assertEquals(
            SharedRoute(SharedPlace(null, stephansplatz), SharedPlace(null, westbahnhof)),
            SharedRoutes.parse(
                "https://www.openstreetmap.org/directions?engine=fossgis_osrm_foot&route=48.2085%2C16.3731%3B48.1966%2C16.3372",
            ),
        )
        assertEquals(
            SharedRoute(null, SharedPlace(null, stephansplatz)),
            SharedRoutes.parse("https://www.openstreetmap.org/?mlat=48.2085&mlon=16.3731#map=17/48.2085/16.3731"),
        )
        assertEquals(
            SharedRoute(null, SharedPlace(null, stephansplatz)),
            SharedRoutes.parse("https://osmand.net/go?lat=48.2085&lon=16.3731&z=16"),
        )
        assertNull(SharedRoutes.parse("https://www.openstreetmap.org/#map=17/48.2/16.3"))
    }

    @Test
    fun `bare coordinates and things that are not routes`() {
        assertEquals(SharedRoute(null, SharedPlace(null, stephansplatz)), SharedRoutes.parse("48.2085, 16.3731"))
        assertNull(SharedRoutes.parse("Treffen wir uns am Stephansplatz?"))
        assertNull(SharedRoutes.parse("https://example.com/maps/dir/A/B"))
        assertNull(SharedRoutes.parse("https://maps.google.com/?q=91.0,16.3&x=%ZZ"), "latitude out of range, broken escape")
        assertNull(SharedRoutes.parse("https://exa mple.com/"))
    }
}
