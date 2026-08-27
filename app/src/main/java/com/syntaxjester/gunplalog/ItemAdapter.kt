package com.syntaxjester.gunplalog

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ItemAdapter(
    private val onClick: (Item) -> Unit,
    private val onLongClick: (Item) -> Unit,
    private val onPhotoClick: (Item) -> Unit
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    val data = mutableListOf<Item>()

    fun submit(list: List<Item>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumbCard: MaterialCardView = v.findViewById(R.id.thumbCard)
        val ivThumb: ImageView = v.findViewById(R.id.ivThumb)
        val tvBadge: TextView = v.findViewById(R.id.tvBadge)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvMeta: TextView = v.findViewById(R.id.tvMeta)
        val tvNote: TextView = v.findViewById(R.id.tvNote)
        val tvPrice: TextView = v.findViewById(R.id.tvPrice)
        val tvStatus: TextView = v.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        val ctx = holder.itemView.context
        val gradeColor = safeColor(Grades.color(item.grade), "#9E9E9E")

        holder.tvBadge.text = item.grade
        holder.thumbCard.setCardBackgroundColor(gradeColor)

        // 有美图 → 显示图片，规格标签压在底部；无美图 → 纯规格色块
        val thumb = Photos.decode(ctx, item.photo, 240)
        if (thumb != null) {
            holder.ivThumb.setImageBitmap(thumb)
            holder.ivThumb.visibility = View.VISIBLE
            holder.tvBadge.setBackgroundColor(Color.parseColor("#59000000"))
            holder.ivThumb.setOnClickListener { onPhotoClick(item) }
            holder.ivThumb.isClickable = true
        } else {
            holder.ivThumb.setImageDrawable(null)
            holder.ivThumb.visibility = View.GONE
            holder.tvBadge.setBackgroundColor(Color.TRANSPARENT)
            holder.ivThumb.setOnClickListener(null)
            holder.ivThumb.isClickable = false
        }

        holder.tvName.text = item.name

        val parts = mutableListOf<String>()
        if (item.scale.isNotBlank()) parts.add(item.scale)
        if (item.date.isNotBlank()) parts.add(item.date)
        holder.tvMeta.text = parts.joinToString(" · ")

        if (item.note.isBlank()) {
            holder.tvNote.visibility = View.GONE
        } else {
            holder.tvNote.visibility = View.VISIBLE
            holder.tvNote.text = item.note
        }

        holder.tvPrice.text =
            if (item.price > 0) "¥" + String.format("%.2f", item.price) else "—"

        val sc = statusColor(item.status)
        holder.tvStatus.text = Item.statusName(item.status)
        holder.tvStatus.setTextColor(safeColor(sc, "#2E7D32"))
        holder.tvStatus.background?.mutate()?.setTint(
            safeColor("#22" + sc.removePrefix("#"), "#EEF1F6")
        )

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    private fun safeColor(hex: String, fallback: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.parseColor(fallback)
    }

    private fun statusColor(status: Int): String = when (status) {
        0 -> "#F57C00"
        2 -> "#C62828"
        else -> "#2E7D32"
    }
}
