package com.syntaxjester.gunplalog

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** 展架：有美图的模型以 2 列瀑布式网格展示 */
class ShowCaseAdapter(
    private val onItemClick: (Item) -> Unit,
    private val onPhotoClick: (Item) -> Unit,
    private val onItemLong: (Item) -> Unit
) : RecyclerView.Adapter<ShowCaseAdapter.VH>() {

    val data = mutableListOf<Item>()

    fun submit(list: List<Item>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val iv: ImageView = v.findViewById(R.id.ivPhoto)
        val badge: TextView = v.findViewById(R.id.tvBadge)
        val name: TextView = v.findViewById(R.id.tvName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_showcase, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        holder.badge.text = item.grade
        holder.badge.background?.mutate()?.setTint(safeColor(Grades.color(item.grade), "#9E9E9E"))
        holder.name.text = item.name

        val bmp = Photos.decode(holder.itemView.context, item.photo, 420)
        if (bmp != null) {
            holder.iv.setImageBitmap(bmp)
            holder.iv.visibility = android.view.View.VISIBLE
        } else {
            holder.iv.setImageDrawable(null)
            holder.iv.visibility = android.view.View.GONE
        }

        // 点图片看大图；点卡片其他区域进入编辑；长按才删除。
        holder.iv.setOnClickListener { onPhotoClick(item) }
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLong(item); true }
    }

    private fun safeColor(hex: String, fallback: String): Int = try {
        android.graphics.Color.parseColor(hex)
    } catch (_: Exception) {
        android.graphics.Color.parseColor(fallback)
    }
}
