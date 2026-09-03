package com.syntaxjester.gunplalog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.concurrent.thread

/** 坚果云 / WebDAV 账号设置 */
class CloudSheet : BottomSheetDialogFragment() {

    var onSaved: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_cloud, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etEndpoint = view.findViewById<EditText>(R.id.etEndpoint)
        val etUser = view.findViewById<EditText>(R.id.etUser)
        val etPass = view.findViewById<EditText>(R.id.etPass)
        val etDir = view.findViewById<EditText>(R.id.etDir)
        val tvStatus = view.findViewById<TextView>(R.id.tvCloudStatus)

        val cfg = WebDav.load(requireContext())
        etEndpoint.setText(cfg.endpoint)
        etUser.setText(cfg.user)
        etPass.setText(cfg.password)
        etDir.setText(cfg.dir)

        fun collect(): WebDav.Config = WebDav.Config(
            endpoint = etEndpoint.text.toString().trim().ifBlank { WebDav.DEFAULT_ENDPOINT },
            user = etUser.text.toString().trim(),
            password = etPass.text.toString(),
            dir = etDir.text.toString().trim().ifBlank { "GunplaLog" }
        )

        fun status(msg: String, ok: Boolean? = null) {
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = msg
            tvStatus.setTextColor(
                when (ok) {
                    true -> android.graphics.Color.parseColor("#2E7D32")
                    false -> android.graphics.Color.parseColor("#C62828")
                    else -> android.graphics.Color.parseColor("#8A909C")
                }
            )
        }

        view.findViewById<View>(R.id.btnOpenNutstore).setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.jianguoyun.com/d/account/safety")
                    )
                )
            } catch (_: Exception) {
                toast(getString(R.string.cloud_open_fail))
            }
        }

        view.findViewById<View>(R.id.btnCloudTest).setOnClickListener {
            val c = collect()
            if (!c.configured) {
                status(getString(R.string.cloud_need_all), false)
                return@setOnClickListener
            }
            status(getString(R.string.cloud_testing))
            thread {
                val err = WebDav.test(c) ?: WebDav.ensureDir(c)
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    if (err == null) status(getString(R.string.cloud_test_ok), true)
                    else status(err, false)
                }
            }
        }

        view.findViewById<View>(R.id.btnCloudSave).setOnClickListener {
            val c = collect()
            if (!c.configured) {
                status(getString(R.string.cloud_need_all), false)
                return@setOnClickListener
            }
            WebDav.save(requireContext(), c)
            toast(getString(R.string.cloud_saved))
            onSaved?.invoke()
            dismiss()
        }

        view.findViewById<View>(R.id.btnCloudClear).setOnClickListener {
            WebDav.clear(requireContext())
            etUser.setText("")
            etPass.setText("")
            etEndpoint.setText(WebDav.DEFAULT_ENDPOINT)
            etDir.setText("GunplaLog")
            status(getString(R.string.cloud_cleared), null)
            onSaved?.invoke()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(context ?: return, msg, Toast.LENGTH_SHORT).show()
    }
}
