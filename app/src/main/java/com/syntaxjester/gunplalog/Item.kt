package com.syntaxjester.gunplalog

import org.json.JSONObject

data class Item(
    val id: String,
    var name: String,
    var grade: String,      // 规格，见 Grades.ALL
    var scale: String,      // 如 1/144
    var price: Double,
    var date: String,       // yyyy-MM-dd 购买日期
    var status: Int,        // 0=想买 1=已入手 2=已出
    var note: String,
    var photo: String,      // 模型美图：filesDir/images 下的文件名，空=无图
    val createdAt: Long,
    var category: String = "",   // 二级分类，如「拼装模型」
    var brand: String = "",      // 品牌，如「万代」
    var cabinet: String = "",    // 所属柜子
    var location: String = ""    // 存放位置
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("grade", grade)
        put("scale", scale)
        put("price", price)
        put("date", date)
        put("status", status)
        put("note", note)
        put("photo", photo)
        put("createdAt", createdAt)
        put("category", category)
        put("brand", brand)
        put("cabinet", cabinet)
        put("location", location)
    }

    companion object {
        @JvmStatic
        fun fromJson(o: JSONObject): Item = Item(
            id = o.optString("id", java.util.UUID.randomUUID().toString()),
            name = o.optString("name"),
            grade = o.optString("grade", "HG"),
            scale = o.optString("scale"),
            price = o.optDouble("price", 0.0),
            date = o.optString("date"),
            status = o.optInt("status", 1),
            note = o.optString("note"),
            photo = o.optString("photo"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            category = o.optString("category"),
            brand = o.optString("brand"),
            cabinet = o.optString("cabinet"),
            location = o.optString("location")
        )

        fun statusName(status: Int): String = when (status) {
            0 -> "想买"
            2 -> "已出"
            else -> "已入手"
        }
    }
}