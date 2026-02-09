package com.example.pace.ui.search_box

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.data.model.request.CreateGroupRequest
import com.example.pace.databinding.DialogBookmarkGroupBinding

class AddGroupDialogFragment(
    private val onGroupAdded: (CreateGroupRequest) -> Unit
) : DialogFragment() {

    private var _binding: DialogBookmarkGroupBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: GroupColorAdapter

//    private val colors = listOf(R.color.schedule_5, "#F14C82", "#D8643F", "#53B332", "#51AEED", "#5F46DD")

    private var currentSelectedColor: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogBookmarkGroupBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fun getHexColor(resId: Int): String {
            val colorInt = ContextCompat.getColor(requireContext(), resId)
            return String.format("#%06X", (0xFFFFFF and colorInt))
        }

        val colors = listOf(
            getHexColor(R.color.schedule_5), // 변환된 값 사용
            getHexColor(R.color.schedule_6),
            getHexColor(R.color.schedule_1),
            getHexColor(R.color.schedule_17),
            getHexColor(R.color.schedule_14),
            getHexColor(R.color.schedule_9)
        )

        adapter = GroupColorAdapter(colors) { selectedColor ->
            currentSelectedColor = selectedColor
        }
        binding.rvColors.adapter = adapter
        binding.rvColors.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        // 버튼 클릭
        binding.btnCancelGroup.setOnClickListener { dismiss() }
        binding.btnSaveGroup.setOnClickListener {
            val name = binding.etGroupName.text.toString()
            if (name.isNotBlank()) {
                val newGroupRequest = CreateGroupRequest(
                    groupName = name,
                    groupColor = adapter.getSelectedColor(),
                )
                onGroupAdded(newGroupRequest)
                dismiss()
            } else {
                Toast.makeText(context, "그룹명을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}