package com.riqing.ui.settings

/**
 * 学期节次的来源：自定义或套用校内模板。
 */
enum class SemesterTemplateKind(val label: String) {
    CUSTOM("自定义"),
    NORTH("北校区上课时间模板"),
    SOUTH("南校区上课时间模板"),
}

/**
 * 长安大学教学时间安排表（北/南校区）。前 10 节来自学校发布的教学时间表图片；
 * 第 11 节（晚课 20:50-21:35）依据应用既有数据补齐。
 */
private val northCampusSlots = listOf(
    "08:30" to "09:15",
    "09:20" to "10:05",
    "10:25" to "11:10",
    "11:15" to "12:00",
    "14:00" to "14:45",
    "14:50" to "15:35",
    "15:55" to "16:40",
    "16:45" to "17:30",
    "19:00" to "19:45",
    "19:50" to "20:35",
    "20:50" to "21:35",
)

private val southCampusSlots = listOf(
    "08:00" to "08:45",
    "08:55" to "09:40",
    "10:10" to "10:55",
    "11:05" to "11:50",
    "14:00" to "14:45",
    "14:55" to "15:40",
    "16:00" to "16:45",
    "16:55" to "17:40",
    "19:00" to "19:45",
    "19:55" to "20:40",
    "20:50" to "21:35",
)

/** 返回模板的节次时间列表；自定义返回空列表（保持用户现有节次不变）。 */
fun templateSlots(kind: SemesterTemplateKind): List<Pair<String, String>> = when (kind) {
    SemesterTemplateKind.NORTH -> northCampusSlots
    SemesterTemplateKind.SOUTH -> southCampusSlots
    SemesterTemplateKind.CUSTOM -> emptyList()
}
