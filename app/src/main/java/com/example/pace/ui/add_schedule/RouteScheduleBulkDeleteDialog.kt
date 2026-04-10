package com.example.pace.ui.add_schedule

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogRouteScheduleBulkDeleteBinding

class RouteScheduleBulkDeleteDialog(
    context: Context,
    private val selectedCount: Int,
    private val onConfirm: () -> Unit
) : Dialog(context) {
    private lateinit var binding: DialogRouteScheduleBulkDeleteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogRouteScheduleBulkDeleteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.routeScheduleBulkDeleteTv.text = "일정 ${selectedCount}개를 삭제하시겠습니까?"
        binding.routeScheduleBulkDeleteCancelBtn.setOnClickListener { dismiss() }
        binding.routeScheduleBulkDeleteConfirmBtn.setOnClickListener {
            dismiss()
            onConfirm()
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.3f)
        window?.apply {
            val params = attributes
            params.width = (300 * context.resources.displayMetrics.density).toInt()
            params.height = (148 * context.resources.displayMetrics.density).toInt()
            attributes = params
        }
    }
}
