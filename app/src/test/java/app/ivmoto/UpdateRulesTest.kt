package app.ivmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {
    private val hash = "a".repeat(64)

    private fun json(code: String = "2", name: String = "1.1", url: String = "https://example.com/IVMoto.apk", sha: String = hash, notes: String = "Mejoras") =
        """{"versionCode": $code, "versionName": "$name", "apkUrl": "$url", "sha256": "$sha", "notes": "$notes"}"""

    @Test
    fun a_complete_manifest_is_parsed() {
        val m = UpdateRules.parse(json())
        assertNotNull(m)
        assertEquals(2, m!!.versionCode)
        assertEquals("1.1", m.versionName)
        assertEquals("https://example.com/IVMoto.apk", m.apkUrl)
        assertEquals(hash, m.sha256)
        assertEquals("Mejoras", m.notes)
    }

    @Test
    fun notes_are_optional() {
        val m = UpdateRules.parse("""{"versionCode": 3, "versionName": "1.2", "apkUrl": "https://example.com/a.apk", "sha256": "$hash"}""")
        assertEquals("", m!!.notes)
    }

    @Test
    fun an_apk_over_plain_http_is_rejected() {
        assertNull(UpdateRules.parse(json(url = "http://example.com/IVMoto.apk")))
    }

    @Test
    fun a_bad_hash_is_rejected() {
        assertNull(UpdateRules.parse(json(sha = "xyz")))
        assertNull(UpdateRules.parse(json(sha = "a".repeat(63))))
    }

    @Test
    fun broken_or_incomplete_json_is_rejected() {
        assertNull(UpdateRules.parse("not json"))
        assertNull(UpdateRules.parse("""{"versionName": "1.1"}"""))
        assertNull(UpdateRules.parse(json(code = "0")))
    }

    @Test
    fun only_a_higher_version_code_counts_as_an_update() {
        assertTrue(UpdateRules.isNewer(5, 4))
        assertFalse(UpdateRules.isNewer(4, 4))
        assertFalse(UpdateRules.isNewer(3, 4))
    }

    @Test
    fun hashes_compare_ignoring_case_and_spaces() {
        assertTrue(UpdateRules.hashMatches("ABCDEF0123".padEnd(64, '0'), " abcdef0123".padEnd(65, '0').trim()))
        assertFalse(UpdateRules.hashMatches(hash, "b".repeat(64)))
    }

    @Test
    fun a_check_is_due_once_a_day() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(UpdateRules.checkDue(nowMs = day * 3, lastMs = 0L))
        assertTrue(UpdateRules.checkDue(nowMs = day * 2, lastMs = day))
        assertFalse(UpdateRules.checkDue(nowMs = day + 1000, lastMs = day))
    }

    @Test
    fun a_manifest_saved_with_a_byte_order_mark_is_still_read() {
        val m = UpdateRules.parse("\uFEFF" + json())
        assertNotNull(m)
        assertEquals(2, m!!.versionCode)
    }

    @Test
    fun a_bounded_read_returns_a_small_stream_whole() {
        val text = UpdateRules.readLimited("hola {\"a\": 1}".byteInputStream(), 1024)
        assertEquals("hola {\"a\": 1}", text)
    }

    @Test
    fun a_bounded_read_stops_at_the_limit() {
        val text = UpdateRules.readLimited("x".repeat(5000).byteInputStream(), 100)
        assertEquals(100, text.length)
    }

    @Test
    fun a_bounded_read_of_nothing_is_empty() {
        assertEquals("", UpdateRules.readLimited(ByteArray(0).inputStream(), 100))
    }
}
