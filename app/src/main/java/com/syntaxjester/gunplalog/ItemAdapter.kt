package com.syntaxjester.gunplalog

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class ItemEntry {
    data class Header(val label: String, val count: Int, val total: Double) : ItemEntry()
    data class ItemRow(val item: Item) : ItemEntry()
}

class ItemAdapter(
    private val onClick: (Item) -> Unit,
    private val onLongClick: (Item) -> Unit,
    private val onPhotoClick: (Item) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val data = mutableListOf<ItemEntry>()

    fun submit(list: List<ItemEntry>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvGroupTitle)
        val tvStat: TextView = v.findViewById(R.id.tvGroupStat)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumbCard: MaterialCardView = v.findViewById(R.id.thumbCard)
        val ivThumb: ImageView = v.findViewById(R.id.ivThumb)
        val tvBadge: TextView = v.findViewById(R.id.tvBadge)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvMeta: TextView = v.findViewById(R.id.tvMeta)
        val tvCat: TextView = v.findViewById(R.id.tvCat)
        val tvNote: TextView = v.findViewById(R.id.tvNote)
        val tvPrice: TextView = v.findViewById(R.id.tvPrice)
        val tvStatus: TextView = v.findViewById(R.id.tvStatus)
    }

    override fun getItemViewType(position: Int): Int = when (data[position]) {
        is ItemEntry.Header -> 0
        is ItemEntry.ItemRow -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val infl = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderVH(infl.inflate(R.layout.item_group_header, parent, false))
        } else {
            VH(infl.inflate(R.layout.item_model, parent, false))
        }
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = data[position]) {
            is ItemEntry.Header -> {
                val h = holder as HeaderVH
                h.tvTitle.text = entry.label
                h.tvStat.text = "${entry.count} 件" +
                        if (entry.total > 0) " · ¥" + String.format("%.2f", entry.total) else ""
            }
            is ItemEntry.ItemRow -> bindItem(holder as VH, entry.item)
        }
    }

    private fun bindItem(holder: VH, item: Item) {
        val ctx = holder.itemView.context
        val gradeColor = safeColor(Grades.color(item.grade), "#9E9E9E")

        holder.tvBadge.text = item.grade
        holder.thumbCard.setCardBackgroundColor(gradeColor)

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

        val prefs = ctx.getSharedPreferences("gunplalog", android.content.Context.MODE_PRIVATE)
        val showDateService = prefs.getBoolean("showDateService", false)
        val showAverageCost = prefs.getBoolean("showAverageCost", false)

        val parts = mutableListOf<String>()
        if (item.scale.isNotBlank()) parts.add(item.scale)
        if (item.brand.isNotBlank()) parts.add(item.brand)
        if (item.cabinet.isNotBlank()) parts.add(item.cabinet)
        if (item.location.isNotBlank()) parts.add(item.location)
        if (showDateService && item.date.isNotBlank()) {
            parts.add(item.date)
            serviceDays(item)?.let { parts.add("服役 $it 天") }
        }
        holder.tvMeta.text = parts.joinToString(" · ")

        holder.tvCat.visibility = if (item.category.isNotBlank()) View.VISIBLE else View.GONE
        holder.tvCat.text = item.category

        if (item.note.isBlank()) {
            holder.tvNote.visibility = View.GONE
        } else {
            holder.tvNote.visibility = View.VISIBLE
            holder.tvNote.text = item.note
        }

        val priceText = if (item.price > 0) "¥" + String.format("%.2f", item.price) else "—"
        holder.tvPrice.text = if (showAverageCost && item.price > 0) {
            serviceDays(item)?.let { days ->
                "$priceText  日均 ¥" + String.format("%.2f", item.price / days.coerceAtLeast(1))
            } ?: priceText
        } else priceText

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

    private fun serviceDays(item: Item): Int? {
        if (item.date.isBlank() || item.status != 1) return null
        return try {
            val start = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(item.date)?.time ?: return null
            val today = Calendar.getInstance().timeInMillis
            ((today - start) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            null
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
