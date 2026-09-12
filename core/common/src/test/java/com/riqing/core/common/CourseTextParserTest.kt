package com.riqing.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTextParserTest {

    @Test
    fun `示例文本全部行可解析`() {
        val result = CourseTextParser.parse(CourseTextParser.SAMPLE)
        assertEquals(0, result.failedLines.size)
        assertEquals(9, result.sessions.size)

        val first = result.sessions[0]
        assertEquals("高等数学A(1)", first.name)
        assertEquals(listOf(1), first.weekdays)
        assertEquals(1, first.periodStart)
        assertEquals(2, first.periodEnd)
        assertEquals("1-16", first.weeksSpec)
        assertEquals("教1-201", first.location)
        assertEquals("张伟", first.teacher)
    }

    @Test
    fun `双周课程识别`() {
        val result = CourseTextParser.parse("数据结构实验 星期三 3-4节 2-15周(双周) 实验楼B402 赵敏")
        assertEquals(0, result.failedLines.size)
        val s = result.sessions.single()
        assertEquals("数据结构实验", s.name)
        assertEquals("2-15", s.weeksSpec)
        assertTrue(s.isBiweekly)
        assertEquals(false, s.biweeklyOdd)
        assertEquals("实验楼B402", s.location)
    }

    @Test
    fun `单周课程识别`() {
        val s = CourseTextParser.parse("体育(篮球) 星期四 5-6节 3-16周(单周) 体育馆 孙洁").sessions.single()
        assertEquals("3-16", s.weeksSpec)
        assertTrue(s.isBiweekly)
        assertEquals(true, s.biweeklyOdd)
        assertEquals("体育馆", s.location)
        assertEquals("孙洁", s.teacher)
    }

    @Test
    fun `一行多星期合并为一门课`() {
        val result = CourseTextParser.parse(
            """
            高等数学 星期一1-2节 1-16周 教1-201 张伟
            高等数学 星期三1-2节 1-16周 教1-201 张伟
            """.trimIndent(),
        )
        assertEquals(1, result.sessions.size)
        assertEquals(listOf(1, 3), result.sessions[0].weekdays)
    }

    @Test
    fun `未标注周次返回空`() {
        val s = CourseTextParser.parse("体育 星期五 5-6节 田径场 王五").sessions.single()
        assertEquals("", s.weeksSpec)
        assertNull(s.biweeklyOdd)
        assertEquals("田径场", s.location)
    }

    @Test
    fun `字段顺序不限`() {
        val s = CourseTextParser.parse("星期一 1-2节 1-16周 高等数学 张伟 教1-201").sessions.single()
        assertEquals("高等数学", s.name)
        assertEquals("张伟", s.teacher)
        assertEquals("教1-201", s.location)
        assertEquals("1-16", s.weeksSpec)
    }

    @Test
    fun `无法识别的行进入失败列表`() {
        val result = CourseTextParser.parse("这是一段说明文字\n高等数学 星期一 1-2节 1-16周 教1-201 张伟")
        assertEquals(listOf("这是一段说明文字"), result.failedLines)
        assertEquals(1, result.sessions.size)
    }

    @Test
    fun `全角空格与全角标点兼容`() {
        val s = CourseTextParser.parse("高等数学　星期一　1-2节　1-16周　教1-201　张伟").sessions.single()
        assertEquals("高等数学", s.name)
        assertEquals("1-16", s.weeksSpec)
    }

    @Test
    fun `逗号周次列表`() {
        val s = CourseTextParser.parse("选修课 星期二 3节 1,3,5周 教1-101").sessions.single()
        assertEquals("1,3,5", s.weeksSpec)
        assertEquals(3 to 3, s.periodStart to s.periodEnd)
    }

    @Test
    fun `单节次与星期别名`() {
        val s = CourseTextParser.parse("自习 第7节 周天").sessions.single()
        assertEquals(7 to 7, s.periodStart to s.periodEnd)
        assertEquals(listOf(7), s.weekdays)
        assertEquals("", s.weeksSpec)
    }

    @Test
    fun `教室门牌号后跟周姓教师不误判周次`() {
        val s = CourseTextParser.parse("程序设计基础(Java) 星期五 3-4节 1-16周 教3-201 周涛").sessions.single()
        assertEquals("1-16", s.weeksSpec)
        assertEquals("教3-201", s.location)
        assertEquals("周涛", s.teacher)
    }
}
