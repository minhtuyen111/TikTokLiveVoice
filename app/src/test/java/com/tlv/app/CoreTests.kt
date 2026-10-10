package com.tlv.app

import com.tlv.app.auth.Validators
import com.tlv.app.filter.CommentFilter
import com.tlv.app.filter.Reason
import com.tlv.app.live.ChannelParser
import com.tlv.app.live.LiveSource
import com.tlv.app.settings.AppSettings
import org.junit.Assert.*
import org.junit.Test

class CoreTests {
    private val s = AppSettings()

    @Test fun usernameRules() {
        assertNull(Validators.username("viet_123"))
        assertNotNull(Validators.username("ab"))
        assertNotNull(Validators.username("có dấu"))
    }

    @Test fun passwordRules() {
        assertNotNull(Validators.password("short1"))
        assertNotNull(Validators.password("onlyletters"))
        assertNull(Validators.password("abcdefg1"))
        assertTrue(Validators.strength("Abcdefgh1234!") > Validators.strength("abcdefg1"))
    }

    @Test fun channelParsing() {
        assertEquals("abc.def", ChannelParser.parse("@abc.def"))
        assertEquals("abc", ChannelParser.parse("https://www.tiktok.com/@abc/live"))
        assertNull(ChannelParser.parse("not valid!"))
        assertNull(ChannelParser.parse(""))
    }

    @Test fun bridgeUrlMustBeWss() {
        val src = LiveSource()
        assertNull(src.buildUrl("ws://insecure.example.com", "abc"))
        assertTrue(src.buildUrl("wss://bridge.example.com/live", "abc")!!.contains("channel=abc"))
    }

    @Test fun filterRejectsEmptyAndEmojiOnly() {
        assertEquals(Reason.EMPTY, CommentFilter.decide(s, "   ", emptyList()).reason)
        assertEquals(Reason.EMPTY, CommentFilter.decide(s, "😀😀", emptyList()).reason)
    }

    @Test fun filterStripsEmojiFromSpeech() {
        val d = CommentFilter.decide(s, "xin chào 😀", emptyList())
        assertTrue(d.accepted)
        assertEquals("xin chào", d.text)
    }

    @Test fun filterBlockDuplicateLengthPriority() {
        val st = s.copy(blockWords = listOf("spam"), priorityWords = listOf("quà"), maxLength = 10)
        val st2 = s.copy(blockWords = listOf("spam"))
        assertEquals(Reason.BLOCKED, CommentFilter.decide(st2, "mua SPAM nè", emptyList()).reason)
        assertEquals(Reason.TOO_LONG, CommentFilter.decide(st, "một bình luận rất là dài", emptyList()).reason)
        assertEquals(Reason.DUPLICATE, CommentFilter.decide(st, "Xin  chào", listOf(CommentFilter.key("xin chào"))).reason)
        assertTrue(CommentFilter.decide(st, "tặng quà", emptyList()).priority)
    }

    @Test fun controlCharsRemoved() {
        assertEquals("a b", CommentFilter.clean("a\u0000\u0007 b", true))
    }
}
