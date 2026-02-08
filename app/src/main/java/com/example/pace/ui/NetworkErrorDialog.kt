package com.example.pace.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import com.example.pace.databinding.DialogNetworkErrorBinding

class NetworkErrorDialog(
    context: Context,
    private val reload: () -> Unit
    ): Dialog(context) {
    private lateinit var binding: DialogNetworkErrorBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogNetworkErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.networkErrorWifiSettingBtn.setOnClickListener {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            dismiss()
        }
        binding.networkErrorReloadBtn.setOnClickListener {
            reload()
            dismiss()
        }
    }
    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}