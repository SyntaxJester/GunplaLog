package com.syntaxjester.gunplalog

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ManagedEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var template: String = "通用柜子",
    var photo: String = "",
    var children: MutableList<String> = mutableListOf()
) {
    fun json() = JSONObject().apply {
        put("id", id); put("name", name); put("template", template); put("photo", photo)
        put("children", JSONArray().apply { children.forEach { put(it) } })
    }
    companion object {
        fun from(o: JSONObject): ManagedEntry {
            val children = mutableListOf<String>()
            o.optJSONArray("children")?.let { a -> for (i in 0 until a.length()) children.add(a.optString(i)) }
            return ManagedEntry(o.optString("id", UUID.randomUUID().toString()), o.optString("name"),
                o.optString("template", "通用柜子"), o.optString("photo"), children)
        }
    }
}

/** 分类、位置、柜子的结构化本地数据；兼容旧版本的换行名称存储。 */
object AssetStore {
    private const val PREF = "gunplalog"
    fun entries(ctx: Context, key: String): MutableList<ManagedEntry> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key, "").orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return try {
            val a = JSONArray(raw)
            MutableList(a.length()) { ManagedEntry.from(a.getJSONObject(it)) }
        } catch (_: Exception) {
            raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }.map { ManagedEntry(name = it) }.toMutableList()
        }
    }
    fun save(ctx: Context, key: String, values: List<ManagedEntry>) {
        val a = JSONArray(); values.forEach { a.put(it.json()) }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(key, a.toString()).apply()
    }
    fun categories(ctx: Context): MutableList<ManagedEntry> {
        val saved = entries(ctx, "categories")
        if (saved.isNotEmpty()) return saved
        val defaults = mutableListOf(
            ManagedEntry(name = "模玩手办", children = mutableListOf("拼装模型", "成品模型", "手办", "潮玩盲盒", "积木", "微缩模型")),
            ManagedEntry(name = "谷子周边", children = mutableListOf("徽章", "立牌", "卡牌", "挂件", "色纸", "海报", "玩偶")),
            ManagedEntry(name = "美妆", children = mutableListOf("彩妆", "护肤", "香水", "美甲", "工具")),
            ManagedEntry(name = "数码电子", children = mutableListOf("手机", "耳机", "相机", "电脑", "游戏设备")),
            ManagedEntry(name = "美食", children = mutableListOf("零食", "饮品", "咖啡", "茶", "食材")),
            ManagedEntry(name = "衣物", children = mutableListOf("上衣", "裤装", "鞋履")),
            ManagedEntry(name = "饰品", children = mutableListOf("首饰", "包袋", "帽子", "眼镜"))
        )
        save(ctx, "categories", defaults)
        return defaults
    }
}
