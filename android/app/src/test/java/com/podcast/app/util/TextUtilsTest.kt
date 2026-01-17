package com.podcast.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextUtilsTest {

    @Test
    fun `stripHtml removes basic HTML tags`() {
        val html = "<p>Hello World</p>"
        val result = TextUtils.stripHtml(html)
        assertThat(result).isEqualTo("Hello World")
    }

    @Test
    fun `stripHtml removes nested tags`() {
        val html = "<div><p>Hello <strong>World</strong></p></div>"
        val result = TextUtils.stripHtml(html)
        assertThat(result).isEqualTo("Hello World")
    }

    @Test
    fun `stripHtml removes anchor tags with attributes`() {
        val html = """<p>Watch here: <a target="_blank" href="https://example.com">Link</a></p>"""
        val result = TextUtils.stripHtml(html)
        assertThat(result).isEqualTo("Watch here: Link")
    }

    @Test
    fun `stripHtml decodes HTML entities`() {
        val html = "Hello &amp; World &lt;test&gt;"
        val result = TextUtils.stripHtml(html)
        assertThat(result).isEqualTo("Hello & World <test>")
    }

    @Test
    fun `stripHtml decodes numeric entities`() {
        val html = "Hello&#39;s World"
        val result = TextUtils.stripHtml(html)
        assertThat(result).isEqualTo("Hello's World")
    }

    @Test
    fun `stripHtml collapses whitespace`() {
        val html = "<p>Hello</p>   <p>World</p>"
        val result = TextUtils.stripHtml(html)
        assertThat(result).isEqualTo("Hello World")
    }

    @Test
    fun `stripHtml returns null for null input`() {
        val result = TextUtils.stripHtml(null)
        assertThat(result).isNull()
    }

    @Test
    fun `stripHtml returns null for blank input`() {
        val result = TextUtils.stripHtml("   ")
        assertThat(result).isNull()
    }

    @Test
    fun `stripHtml returns null for empty HTML tags`() {
        val result = TextUtils.stripHtml("<p>   </p>")
        assertThat(result).isNull()
    }

    @Test
    fun `stripHtml handles real podcast description`() {
        val html = "<p>Watch here: https://youtu.be/zobiv1u9oJk</p>"
        val result = TextUtils.stripHtml(html)
        assertThat(result).isEqualTo("Watch here: https://youtu.be/zobiv1u9oJk")
    }

    @Test
    fun `stripHtml handles complex real-world HTML`() {
        val html = "<p>Bonus content starts at 01:03:57. </p><p></p><p>Thanks to <a target=\"_blank\" href=\"https://example.com\">our sponsor</a></p>"
        val result = TextUtils.stripHtml(html)
        assertThat(result).isEqualTo("Bonus content starts at 01:03:57. Thanks to our sponsor")
    }
}
