package com.example.pace.ui.search_box.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.pace.R
import com.example.pace.data.model.response.GroupItem
import com.example.pace.data.viewmodel.GroupViewModel
import com.example.pace.databinding.BottomSheetGroupSelectBinding
import com.example.pace.ui.search_box.AddGroupDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupSelectBottomSheet(
    private val mode: Mode,
    private val placeName: String? = null, // 저장 모드일 때만 사용될 장소명
    private val onConfirm: (Long, String?) -> Unit // (groupId, changedName)
) : BottomSheetDialogFragment() {

    enum class Mode { SAVE, MOVE }

    private var _binding: BottomSheetGroupSelectBinding? = null
    private val binding get() = _binding!!
    private val groupViewModel: GroupViewModel by activityViewModels()
    private lateinit var radioAdapter: GroupRadioAdapter
    private val groupList = mutableListOf<GroupItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetGroupSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        groupViewModel.clearErrorState()

        setupRecyclerView()
        setupUI()
        setupListeners()
        observeViewModel()

        groupViewModel.fetchGroupList()
    }

    private fun observeViewModel() {
        groupViewModel.groupList.observe(viewLifecycleOwner) { groups ->
            groupList.clear()
            groupList.addAll(groups)

            if (::radioAdapter.isInitialized) {
                radioAdapter.updateItems(groupList)
            }
        }

        groupViewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (msg.isNullOrBlank()) return@observe

            val isDuplicateError = (groupViewModel.errorCode.value == "PLACE400_1") || msg.contains("동일한 장소")

            if (!isDuplicateError) {
            }
        }

        groupViewModel.errorCode.observe(viewLifecycleOwner) { code ->
            android.util.Log.d("BottomSheetLog", "--> errorCode 들어옴: [$code]")
            when (code) {
                "PLACE400_1" -> {
                    binding.tvErrorMsg.text = "*해당 그룹에 장소가 존재합니다."
                    binding.tvErrorMsg.visibility = View.VISIBLE

                    binding.btnSave.isEnabled = true
                }
                else -> {
                    binding.tvErrorMsg.visibility = View.GONE
                }
            }
        }

        groupViewModel.isOperationSuccess.observe(viewLifecycleOwner) { isSuccess ->
            if(isSuccess) {
//                val dialog = parentFragmentManager.findFragmentByTag("AddGroupDialog") as? AddGroupDialogFragment
//                dialog?.dismiss()
                dismiss()
            }
        }
    }

    private fun setupUI() {
        binding.btnSave.isEnabled = false
        binding.tvErrorMsg.visibility = View.GONE
        if (mode == Mode.MOVE) {
            binding.tvSheetTitle.text = "이동"
            binding.layoutInputContainer.visibility = View.GONE
            binding.btnSave.text = "이동"
        } else {
            binding.tvSheetTitle.text = placeName ?: "장소 저장"
            binding.layoutInputContainer.visibility = View.VISIBLE
            binding.etPlaceMemo.setText("")
            binding.etPlaceMemo.hint = placeName
            binding.btnSave.text = "저장"

            binding.etPlaceMemo.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    binding.tvErrorMsg.visibility = View.GONE
                    updateSaveButtonState()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        updateSaveButtonState()
    }

    private fun setupRecyclerView() {
        radioAdapter = GroupRadioAdapter(
            items = groupList,
            onAddClick = { // 기존의 그룹 추가 다이얼로그 콜백
                val dialog = AddGroupDialogFragment { request ->
                    groupViewModel.createGroup(request.groupName, request.groupColor)
                }
                dialog.show(parentFragmentManager, "AddGroupDialog")
            },
            onItemClick = {
                binding.tvErrorMsg.visibility = View.GONE
                updateSaveButtonState()
            }
        )

        binding.rvGroupList.adapter = radioAdapter
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            binding.tvErrorMsg.visibility = View.GONE

            val selectedGroupId = radioAdapter.getSelectedGroupId()


            if (mode == Mode.SAVE) {
                val inputName = binding.etPlaceMemo.text.toString()

                val regex = "^[a-zA-Z0-9가-힣ㄱ-ㅎㅏ-ㅣ\\s]+$".toRegex()

                if (!regex.matches(inputName)) {
                    return@setOnClickListener
                }

                onConfirm(selectedGroupId, inputName)
            } else {
                onConfirm(selectedGroupId, null)
            }
        }
    }

    private fun updateSaveButtonState() {
        val isGroupSelected = radioAdapter.getSelectedGroupId() != -1L

        val isStateValid = if (mode == Mode.SAVE) {
            val hasText = binding.etPlaceMemo.text.toString().trim().isNotEmpty()
            isGroupSelected && hasText
        } else {
            isGroupSelected
        }

        binding.btnSave.isEnabled = isStateValid
        val buttonColor = if (isStateValid) {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.semantic_info)
        } else {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_disabled)
        }
        binding.btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonColor)
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let { sheet ->
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels

            // 바텀시트 높이를 화면의 80%로 고정
            val layoutParams = sheet.layoutParams
            layoutParams.height = (screenHeight * 0.6).toInt()
            sheet.layoutParams = layoutParams

            // 동작 설정
            val behavior = BottomSheetBehavior.from(sheet)

            // 처음 떴을 때 상태:
            // STATE_EXPANDED로 하면 0.8 높이까지
            behavior.state = BottomSheetBehavior.STATE_EXPANDED

            // 드래그해서 접는 기능 끄기 (선택사항)
            // true로 하면 '반만 접히는' 단계 없이 닫히거나/열리거나 둘 중 하나가 됩니다.
            behavior.skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}