package com.syntaxjester.gunplalog

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.json.JSONObject
import java.io.File
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

    /** 当前选中的美图文件名（已落盘到 filesDir/images） */
    private var pickedPhoto: String = ""

    /** 本次会话新导入但可能被丢弃的图片，取消时清理 */
    private val staged = mutableListOf<String>()

    private var pendingCameraFile: File? = null

    private lateinit var ivPhoto: ImageView
    private lateinit var btnPhotoRemove: View

    private val cameraLauncher =
        registerForActivityResult(TakePictureGranting()) { ok ->
            val f = pendingCameraFile
            pendingCameraFile = null
            if (ok && f != null) {
                val name = Photos.importFromFile(requireContext(), f)
                try {
                    f.delete()
                } catch (_: Exception) {
                }
                if (name != null) applyPhoto(name) else toast(R.string.photo_fail)
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val name = Photos.importFrom(requireContext(), uri)
                if (name != null) applyPhoto(name) else toast(R.string.photo_fail)
            }
        }

    private val cameraPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera() else toast(R.string.photo_camera_denied)
        }

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
        val flowStatus = view.findViewById<FlowLayout>(R.id.flowStatus)
        val tvGrade = view.findViewById<TextView>(R.id.tvGrade)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etScale = view.findViewById<EditText>(R.id.etScale)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etPrice = view.findViewById<EditText>(R.id.etPrice)
        val etNote = view.findViewById<EditText>(R.id.etNote)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        ivPhoto = view.findViewById(R.id.ivPhoto)
        btnPhotoRemove = view.findViewById(R.id.btnPhotoRemove)

        val e = existing
        pickedGrade = if (e != null) Grades.normalize(e.grade) else Grades.DEFAULT
        pickedStatus = e?.status ?: 1
        pickedPhoto = e?.photo ?: ""

        tvGrade.text = pickedGrade
        tvGrade.setOnClickListener {
            val opts = Grades.ALL.toTypedArray()
            val cur = opts.indexOf(pickedGrade).coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.grade_pick_title)
                .setSingleChoiceItems(opts, cur) { dlg, which ->
                    pickedGrade = opts[which]
                    tvGrade.text = pickedGrade
                    dlg.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }


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

        renderPhoto()

        view.findViewById<View>(R.id.btnCamera).setOnClickListener { requestCamera() }
        view.findViewById<View>(R.id.btnGallery).setOnClickListener { launchGallery() }
        btnPhotoRemove.setOnClickListener { clearPhoto() }
        ivPhoto.setOnClickListener {
            if (pickedPhoto.isNotBlank()) {
                PhotoViewer.show(requireContext(), pickedPhoto)
            } else {
                requestCamera()
            }
        }

        // 日期：可手动输入，输入时自动补 '-'；日历按钮弹选择器
        etDate.addTextChangedListener(DateAutoFormat(etDate))
        view.findViewById<View>(R.id.btnDatePick).setOnClickListener {
            val cal = Calendar.getInstance()
            val cur = etDate.text.toString().trim()
            parseDate(cur)?.let { cal.time = it }
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    etDate.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
                    etDate.setSelection(etDate.text.length)
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
                toast(R.string.name_required)
                return@setOnClickListener
            }
            // 手输日期做一次校验与规范化（空日期允许，比如「想买」还没买）
            val rawDate = etDate.text.toString().trim()
            val normDate: String
            if (rawDate.isEmpty()) {
                normDate = ""
            } else {
                val d = parseDate(rawDate)
                if (d == null) {
                    etDate.error = getString(R.string.date_invalid)
                    toast(R.string.date_invalid)
                    return@setOnClickListener
                }
                normDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
            }
            val saved = Item(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name,
                grade = pickedGrade,
                scale = etScale.text.toString().trim(),
                price = etPrice.text.toString().toDoubleOrNull() ?: 0.0,
                date = normDate,
                status = pickedStatus,
                note = etNote.text.toString().trim(),
                photo = pickedPhoto,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            // 编辑时换了图 → 删掉旧图；本次导入但未采用的图也一并清理
            existing?.photo?.let { old ->
                if (old.isNotBlank() && old != pickedPhoto) Photos.delete(requireContext(), old)
            }
            staged.filter { it != pickedPhoto }.forEach { Photos.delete(requireContext(), it) }
            staged.clear()
            onSaved?.invoke(saved)
            dismiss()
        }
    }

    override fun onDestroyView() {
        // 未保存就关闭：清掉本次导入的临时图片
        staged.forEach { Photos.delete(requireContext(), it) }
        staged.clear()
        super.onDestroyView()
    }

    // ---------- 美图 ----------

    private fun applyPhoto(name: String) {
        // 同一次编辑里连续换图，前一张直接回收
        if (pickedPhoto.isNotBlank() && pickedPhoto in staged && pickedPhoto != name) {
            Photos.delete(requireContext(), pickedPhoto)
            staged.remove(pickedPhoto)
        }
        pickedPhoto = name
        staged.add(name)
        renderPhoto()
    }

    private fun clearPhoto() {
        if (pickedPhoto.isNotBlank() && pickedPhoto in staged) {
            Photos.delete(requireContext(), pickedPhoto)
            staged.remove(pickedPhoto)
        }
        pickedPhoto = ""
        renderPhoto()
    }

    private fun renderPhoto() {
        val ctx = context ?: return
        val bmp = Photos.decode(ctx, pickedPhoto, 480)
        if (bmp != null) {
            ivPhoto.setImageBitmap(bmp)
            ivPhoto.setPadding(0, 0, 0, 0)
            ivPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
            btnPhotoRemove.visibility = View.VISIBLE
        } else {
            ivPhoto.setImageResource(R.drawable.ic_camera)
            val p = (30 * resources.displayMetrics.density).toInt()
            ivPhoto.setPadding(p, p, p, p)
            ivPhoto.scaleType = ImageView.ScaleType.FIT_CENTER
            btnPhotoRemove.visibility = View.GONE
        }
    }

    private fun requestCamera() {
        val ctx = context ?: return
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val ctx = context ?: return
        try {
            val f = Photos.cameraTemp(ctx)
            pendingCameraFile = f
            cameraLauncher.launch(Photos.uriFor(ctx, f))
        } catch (_: Exception) {
            pendingCameraFile = null
            toast(R.string.photo_no_camera)
        }
    }

    private fun launchGallery() {
        try {
            galleryLauncher.launch("image/*")
        } catch (_: Exception) {
            toast(R.string.photo_no_gallery)
        }
    }

    private fun toast(res: Int) {
        Toast.makeText(context ?: return, getString(res), Toast.LENGTH_SHORT).show()
    }

    private fun trimPrice(p: Double): String =
        if (p == p.toLong().toDouble()) p.toLong().toString()
        else String.format(Locale.US, "%.2f", p)

    /**
     * 宽松解析日期：接受 2026-09-02 / 2026/9/2 / 20260902 等写法，
     * 并用 setLenient(false) 拦掉 2026-02-31 这类不存在的日期。
     */
    private fun parseDate(raw: String): Date? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val digits = s.filter { it.isDigit() }
        val norm = when {
            digits.length == 8 ->
                digits.substring(0, 4) + "-" + digits.substring(4, 6) + "-" + digits.substring(6, 8)
            else -> s.replace('/', '-').replace('.', '-')
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(norm)
        } catch (_: Exception) {
            null
        }
    }

    /** 手输时自动在 4、6 位后补 '-'，省得自己敲分隔符 */
    private class DateAutoFormat(private val et: EditText) : android.text.TextWatcher {
        private var busy = false

        override fun afterTextChanged(s: android.text.Editable?) {
            if (busy || s == null) return
            val digits = s.toString().filter { it.isDigit() }.take(8)
            val sb = StringBuilder()
            for ((i, c) in digits.withIndex()) {
                if (i == 4 || i == 6) sb.append('-')
                sb.append(c)
            }
            val formatted = sb.toString()
            if (formatted != s.toString()) {
                busy = true
                et.setText(formatted)
                et.setSelection(formatted.length)
                busy = false
            }
        }

        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    }
}
