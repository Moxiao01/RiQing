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
        assertEquals(11, result.sessions.size)

        val first = result.sessions[0]
        assertEquals("高等数学A(1)", first.name)
        assertEquals(listOf(1), first.weekdays)
        assertEquals(1, first.periodStart)
        assertEquals(2, first.periodEnd)
        assertEquals("1-16", first.weeksSpec)
        assertEquals("教1-201", first.location)
        assertEquals("张伟", first.teacher)

        assertEquals("WM1403", result.sessions[9].location)
        assertEquals("3,8,11,14", result.sessions[9].weeksSpec)
        assertEquals("赵永华,贾夏", result.sessions[10].teacher)
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

    @Test
    fun `字母开头门牌教室识别`() {
        val s = CourseTextParser.parse("地球物理概论 星期一 1-2节 1-4周 WM1103 邵广周").sessions.single()
        assertEquals("地球物理概论", s.name)
        assertEquals("WM1103", s.location)
        assertEquals("邵广周", s.teacher)
        assertEquals("1-4", s.weeksSpec)
    }

    @Test
    fun `带井号的门牌教室识别`() {
        val s = CourseTextParser.parse("土地整治施工与管理 星期二 3-4节 1-8周 WX2106# 李尚颖").sessions.single()
        assertEquals("WX2106#", s.location)
        assertEquals("李尚颖", s.teacher)
    }

    @Test
    fun `星号标记的门牌教室识别`() {
        val s = CourseTextParser.parse("地球物理概论 星期一 1-2节 1-4周 *WM1103 邵广周").sessions.single()
        assertEquals("WM1103", s.location)
        assertEquals("邵广周", s.teacher)
        val trailing = CourseTextParser.parse("高等数学 星期三 3-4节 1-16周 WM1103* 张三").sessions.single()
        assertEquals("WM1103", trailing.location)
        assertEquals("高等数学", trailing.name)
    }

    @Test
    fun `逗号分隔的多教师整串识别`() {
        val s = CourseTextParser.parse("科技论文写作 星期二 5-6节 9-16周 WX2306 赵永华,贾夏").sessions.single()
        assertEquals("WX2306", s.location)
        assertEquals("赵永华,贾夏", s.teacher)
    }

    @Test
    fun `顿号周次列表`() {
        val s = CourseTextParser.parse("大学生心理健康教育 星期三 5-6节 3、8、11、14周 WM1403 郭羽熙").sessions.single()
        assertEquals("3,8,11,14", s.weeksSpec)
        assertEquals("WM1403", s.location)
    }

    @Test
    fun `顿号分隔的多段周次区间`() {
        val s = CourseTextParser.parse(
            "大学生心理健康教育 星期三 5-6节 4-7、9-10、12-13、15-18周 虚拟教室-同步学习平台 郭羽熙",
        ).sessions.single()
        assertEquals("4-7,9-10,12-13,15-18", s.weeksSpec)
        assertEquals("虚拟教室-同步学习平台", s.location)
    }

    @Test
    fun `真实教务课表整段导入`() {
        val result = CourseTextParser.parse(
            """
            地球物理概论 星期一 1-2节 1-4周 WM1103 邵广周
            土地规划设 星期一 3-4节 1-8周 WX2304 任朝霞
            地质工程设计 星期一 7-8节 1-8周 WM3307 刘鑫
            英语畅谈中国文化 星期二 1-2节 1-9周 WX2207 冯丽云
            土地整治施工与管理 星期二 3-4节 1-8周 WX2106# 李尚颖
            科技论文写作 星期二 5-6节 9-16周 WX2306 赵永华,贾夏
            道路安全与环境 星期二 5-6节 3-6周 WM1201 李凯玲
            大学生心理健康教育 星期三 5-6节 3、8、11、14周 WM1403 郭羽熙
            大学生心理健康教育 星期三 5-6节 4-7、9-10、12-13、15-18周 虚拟教室-同步学习平台 郭羽熙
            地质工程设计 星期三 7-8节 1-8周 WM3307 刘鑫
            土地整治施工与管理 星期四 3-4节 1-8周 WX2106# 李尚颖
            科技论文写作 星期四 5-6节 9-16周 WX2306 赵永华,贾夏
            道路安全与环境 星期四 7-8节 3-6周 WM1201 李凯玲
            地球物理概论 星期四 7-8节 1-4周 WM1103 邵广周
            土地整治与生态修复课程设计 星期五 1-2节 13-16周 WX2302 韩磊,杨永琼,谢丹妮,李尚颖,司绍诚,康宏亮
            土地规划设 星期五 3-4节 1-8周 WX2304 任朝霞
            土地整治与生态修复课程设计 星期五 3-4节 13-16周 WX2302 韩磊,杨永琼,谢丹妮,李尚颖,司绍诚,康宏亮
            土地整治与生态修复课程设计 星期五 5-6节 13-16周 WX2302 韩磊,杨永琼,谢丹妮,李尚颖,司绍诚,康宏亮
            英语畅谈中国文化 星期五 5-6节 1-9周 WX2207 冯丽云
            土地整治与生态修复课程设计 星期五 7-8节 13-16周 WX2302 韩磊,杨永琼,谢丹妮,李尚颖,司绍诚,康宏亮
            """.trimIndent(),
        )
        assertEquals(0, result.failedLines.size)
        // 20 行中 4 门课有两个星期重复、其余字段一致，合并后剩 16 门
        assertEquals(16, result.sessions.size)

        val physics = result.sessions[0]
        assertEquals("地球物理概论", physics.name)
        assertEquals("WM1103", physics.location)
        assertEquals("邵广周", physics.teacher)
        assertEquals("1-4", physics.weeksSpec)
        assertEquals(listOf(1), physics.weekdays)

        val merged = result.sessions.filter { it.name == "科技论文写作" }.single()
        assertEquals(listOf(2, 4), merged.weekdays)
        assertEquals("WX2306", merged.location)
        assertEquals("赵永华,贾夏", merged.teacher)

        val mental = result.sessions.filter { it.name == "大学生心理健康教育" }
        assertEquals(2, mental.size)
        assertEquals("3,8,11,14", mental.first { it.location == "WM1403" }.weeksSpec)
        assertEquals(
            "4-7,9-10,12-13,15-18",
            mental.first { it.location == "虚拟教室-同步学习平台" }.weeksSpec,
        )

        val design = result.sessions.filter { it.name == "土地整治与生态修复课程设计" }
        assertEquals(4, design.size)
        assertEquals("韩磊,杨永琼,谢丹妮,李尚颖,司绍诚,康宏亮", design[0].teacher)
    }
}
