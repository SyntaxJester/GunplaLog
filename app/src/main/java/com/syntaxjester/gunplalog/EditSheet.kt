package com.syntaxjester.gunplalog

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class EditSheet : BottomSheetDialogFragment() {

    var onSaved: ((Item) -> Unit)? = null
    private var existing: Item? = null

    private var pickedGrade = Grades.DEFAULT
    private var pickedStatus = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.getString("item")?.let {
            try {
                existing = Item.fromJson(JSONObject(it))
            } catch (_: Exception) {
            }
        }

        val flowGrade = view.findViewById<FlowLayout>(R.id.flowGrade)
        val flowStatus = view.findViewById<FlowLayout>(R.id.flowStatus)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etScale = view.findViewById<EditText>(R.id.etScale)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etPrice = view.findViewById<EditText>(R.id.etPrice)
        val etNote = view.findViewById<EditText>(R.id.etNote)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        val e = existing
        pickedGrade = if (e != null) Grades.normalize(e.grade) else Grades.DEFAULT
        pickedStatus = e?.status ?: 1

        // 规格胶囊（18 个，自动换行）
        Grades.ALL.forEach { g ->
            val pill: TextView = Pills.sheet(requireContext(), g)
            pill.isSelected = (g == pickedGrade)
            pill.setOnClickListener {
                pickedGrade = g
                Pills.select(flowGrade, pill)
            }
            flowGrade.addView(pill)
        }

        // 状态胶囊
        listOf(0 to "想买", 1 to "已入手", 2 to "已出").forEach { (code, label) ->
            val pill: TextView = Pills.sheet(requireContext(), label)
            pill.isSelected = (code == pickedStatus)
            pill.setOnClickListener {
                pickedStatus = code
                Pills.select(flowStatus, pill)
            }
            flowStatus.addView(pill)
        }

        if (e != null) {
            etName.setText(e.name)
            etScale.setText(e.scale)
            etDate.setText(e.date)
            if (e.price > 0) etPrice.setText(trimPrice(e.price))
            etNote.setText(e.note)
        } else {
            etDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
        }

        etDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val cur = etDate.text.toString()
            if (cur.length == 10) {
                try {
                    val parts = cur.split("-")
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                } catch (_: Exception) {
                }
            }
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    etDate.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = getString(R.string.name_required)
                Toast.makeText(context, getString(R.string.name_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val saved = Item(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name,
                grade = pickedGrade,
                scale = etScale.text.toString().trim(),
                price = etPrice.text.toString().toDoubleOrNull() ?: 0.0,
                date = etDate.text.toString().trim(),
                status = pickedStatus,
                note = etNote.text.toString().trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            onSaved?.invoke(saved)
            dismiss()
        }
    }

    private fun trimPrice(p: Double): String =
        if (p == p.toLong().toDouble()) p.toLong().toString()
        else String.format(Locale.US, "%.2f", p)
}
