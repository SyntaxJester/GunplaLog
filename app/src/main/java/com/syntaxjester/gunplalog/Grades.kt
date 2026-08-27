package com.syntaxjester.gunplalog

/** 规格（原「等级」）—— 首页筛选与编辑弹层共用同一份定义 */
object Grades {

    val ALL = listOf(
        "SD", "FM", "RE", "TV", "EG", "HGUC", "HG", "RG", "MG",
        "MGSD", "MGEX", "MEGA", "MB", "PG", "PGU", "HIRM", "工具", "其他"
    )

    const val DEFAULT = "HG"

    fun normalize(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return DEFAULT
        ALL.forEach { if (it.equals(t, ignoreCase = true)) return it }
        return "其他"
    }

    fun color(grade: String): String = when (grade) {
        "SD" -> "#00897B"
        "FM" -> "#795548"
        "RE" -> "#6D4C41"
        "TV" -> "#7CB342"
        "EG" -> "#43A047"
        "HGUC" -> "#FB8C00"
        "HG" -> "#FF8F00"
        "RG" -> "#1E88E5"
        "MG" -> "#8E24AA"
        "MGSD" -> "#AB47BC"
        "MGEX" -> "#7E57C2"
        "MEGA" -> "#3949AB"
        "MB" -> "#546E7A"
        "PG" -> "#D81B60"
        "PGU" -> "#C2185B"
        "HIRM" -> "#00ACC1"
        "工具" -> "#757575"
        else -> "#9E9E9E"
    }
}
