package com.example.pace.ui.main.home

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.example.pace.databinding.DialogModalCaseBinding

class ModalCaseDialog(context: Context): Dialog(context) {
    lateinit var binding: DialogModalCaseBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogModalCaseBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}