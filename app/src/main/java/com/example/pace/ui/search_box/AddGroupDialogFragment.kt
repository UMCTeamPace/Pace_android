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
import com.example.pace.data.model.response.GroupItem
import com.example.pace.databinding.DialogBookmarkGroupBinding

class AddGroupDialogFragment(
    private val groupItem: GroupItem? = null,
    private val onGroupAdded: (CreateGroupRequest) -> Unit
) : DialogFragment() {

    private var _binding: DialogBookmarkGroupBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: GroupColorAdapter


    private var currentSelectedColor: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogBookmarkGroupBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (groupItem != null) {
            binding.tvGroupDialogTitle.text = "그룹 편집"
            binding.etGroupName.setText(groupItem.groupName)
            currentSelectedColor = groupItem.groupColor
            binding.btnSaveGroup.text = "수정"
        } else {
             binding.tvGroupDialogTitle.text = "새 그룹"
            binding.btnSaveGroup.text = "저장"
        }

        fun getHexColor(resId: Int): String {
            val colorInt = ContextCompat.getColor(requireContext(), resId)
            return String.format("#%06X", (0xFFFFFF and colorInt))
        }

        val colors = listOf(
            getHexColor(R.color.schedule_5),
            getHexColor(R.color.schedule_6),
            getHexColor(R.color.schedule_1),
            getHexColor(R.color.schedule_17),
            getHexColor(R.color.schedule_14),
            getHexColor(R.color.schedule_9)
        )

        var initialPosition = 0
        if (groupItem != null) {
            initialPosition = colors.indexOfFirst { it.equals(groupItem.groupColor, ignoreCase = true) }
            if (initialPosition == -1){
                initialPosition = 0
            }
        }

        adapter = GroupColorAdapter(colors) { selectedColor ->
            currentSelectedColor = selectedColor
            binding.tvGroupErrorMessage.visibility = View.GONE
        }

        adapter.setSelectedItem(initialPosition)
        currentSelectedColor = colors[initialPosition]

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
            } else {
                Toast.makeText(context, "그룹명을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etGroupName.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                binding.tvGroupErrorMessage.visibility = View.INVISIBLE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    fun showDuplicateError() {
        binding.tvGroupErrorMessage.text = "이미 존재하는 그룹 이름입니다."
        binding.tvGroupErrorMessage.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()

        val windowManager = requireContext().getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val size = android.graphics.Point()
        display.getSize(size)

        val params: ViewGroup.LayoutParams? = dialog?.window?.attributes
        val deviceWidth = size.x

        params?.width = (deviceWidth * 0.85).toInt()
        params?.height = WindowManager.LayoutParams.WRAP_CONTENT

        dialog?.window?.attributes = params as WindowManager.LayoutParams
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}