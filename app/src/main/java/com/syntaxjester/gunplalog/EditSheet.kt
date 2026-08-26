package com.syntaxjester.gunplalog

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class EditSheet : BottomSheetDialogFragment() {

    var onSaved: ((Item) -> Unit)? = null
    private var existing: Item? = null

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

        val gradeIds = linkedMapOf(
            "HG" to R.id.cHG, "RG" to R.id.cRG, "MG" to R.id.cMG,
            "PG" to R.id.cPG, "SD" to R.id.cSD, "其他" to R.id.cOT
        )
        val statusIds = linkedMapOf(
            0 to R.id.sWant, 1 to R.id.sGot, 2 to R.id.sOut
        )

        val cgGrade = view.findViewById<ChipGroup>(R.id.cgGrade)
        val cgStatus = view.findViewById<ChipGroup>(R.id.cgStatus)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etScale = view.findViewById<EditText>(R.id.etScale)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etPrice = view.findViewById<EditText>(R.id.etPrice)
        val etNote = view.findViewById<EditText>(R.id.etNote)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        cgGrade.isSingleSelection = true
        cgStatus.isSingleSelection = true

        val e = existing
        if (e != null) {
            etName.setText(e.name)
            etScale.setText(e.scale)
            etDate.setText(e.date)
            if (e.price > 0) etPrice.setText(trimPrice(e.price))
            etNote.setText(e.note)
            gradeIds[e.grade]?.let { cgGrade.check(it) }
            statusIds[e.status]?.let { cgStatus.check(it) }
            btnSave.text = getString(R.string.save)
        } else {
            cgGrade.check(R.id.cHG)
            cgStatus.check(R.id.sGot)
            etDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date()))
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
            var grade = "HG"
            for ((g, id) in gradeIds) if (cgGrade.checkedChipId == id) grade = g
            var status = 1
            for ((s, id) in statusIds) if (cgStatus.checkedChipId == id) status = s

            val saved = Item(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name,
                grade = grade,
                scale = etScale.text.toString().trim(),
                price = etPrice.text.toString().toDoubleOrNull() ?: 0.0,
                date = etDate.text.toString().trim(),
                status = status,
                note = etNote.text.toString().trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            onSaved?.invoke(saved)
            dismiss()
        }
    }

    private fun trimPrice(p: Double): String =
        if (p == p.toLong().toDouble()) p.toLong().toString() else String.format(Locale.US, "%.2f", p)
}
