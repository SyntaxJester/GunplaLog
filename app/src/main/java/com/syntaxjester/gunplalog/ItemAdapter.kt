package com.syntaxjester.gunplalog

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ItemAdapter(
    private val onClick: (Item) -> Unit,
    private val onLongClick: (Item) -> Unit
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    val data = mutableListOf<Item>()

    fun submit(list: List<Item>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
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
        holder.tvBadge.text = item.grade
        try {
            holder.tvBadge.background.setTint(Color.parseColor(gradeColor(item.grade)))
        } catch (_: Exception) {
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

        holder.tvStatus.text = Item.statusName(item.status)
        holder.tvStatus.setTextColor(Color.parseColor(statusColor(item.status)))

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    private fun gradeColor(grade: String): String = when (grade) {
        "HG" -> "#FF8F00"
        "RG" -> "#1E88E5"
        "MG" -> "#8E24AA"
        "PG" -> "#D81B60"
        "SD" -> "#00897B"
        else -> "#757575"
    }

    private fun statusColor(status: Int): String = when (status) {
        0 -> "#F57C00"
        2 -> "#C62828"
        else -> "#2E7D32"
    }
}
