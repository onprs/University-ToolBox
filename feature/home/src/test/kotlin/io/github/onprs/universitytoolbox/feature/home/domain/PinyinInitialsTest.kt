package io.github.onprs.universitytoolbox.feature.home.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinInitialsTest {
    @Test
    fun `extractInitials maps common Chinese tool names to expected initials`() {
        assertEquals("xxt", PinyinInitials.extractInitials("学习通"))
        assertEquals("gpajdjsq", PinyinInitials.extractInitials("GPA 绩点计算器"))
        assertEquals("ewmsm", PinyinInitials.extractInitials("二维码扫描"))
        assertEquals("kjqd", PinyinInitials.extractInitials("快捷签到"))
        assertEquals("sz", PinyinInitials.extractInitials("设置"))
    }

    @Test
    fun `matches supports direct text and pinyin initials`() {
        assertTrue(PinyinInitials.matches("学分绩点计算器", "xfjd"))
        assertTrue(PinyinInitials.matches("学分绩点计算器", "jd"))
        assertTrue(PinyinInitials.matches("学分绩点计算器", "绩点"))
        assertTrue(PinyinInitials.matches("学习通课堂辅助", "xxt"))
        assertFalse(PinyinInitials.matches("学习通课堂辅助", "ewm"))
    }
}
