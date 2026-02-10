package com.example.pace.ui.search_box.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
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
    private val groupViewModel: GroupViewModel by viewModels()
    private lateinit var radioAdapter: GroupRadioAdapter
    private val groupList = mutableListOf<GroupItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetGroupSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupRecyclerView()
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
            if(msg.isNotBlank()) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        groupViewModel.isOperationSuccess.observe(viewLifecycleOwner) { isSuccess ->
            if(isSuccess) {
                val dialog = parentFragmentManager.findFragmentByTag("AddGroupDialog") as? AddGroupDialogFragment
                dialog?.dismiss()
            }
        }
    }

    private fun setupUI() {
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
        }
    }

    private fun setupRecyclerView() {
        radioAdapter = GroupRadioAdapter(groupList) {
            val dialog = AddGroupDialogFragment { request ->
                groupViewModel.createGroup(request.groupName, request.groupColor)
            }
            dialog.show(parentFragmentManager, "AddGroupDialog")
        }

        binding.rvGroupList.adapter = radioAdapter
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            val selectedGroupId = radioAdapter.getSelectedGroupId()

            if (selectedGroupId == -1L) {
                Toast.makeText(context, "그룹을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (mode == Mode.SAVE) {
                val inputName = binding.etPlaceMemo.text.toString()

                if (inputName.isBlank()) {
                    Toast.makeText(context, "장소 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val regex = "^[a-zA-Z0-9가-힣ㄱ-ㅎㅏ-ㅣ\\s]+$".toRegex()

                if (!regex.matches(inputName)) {
                    Toast.makeText(context, "특수문자는 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                onConfirm(selectedGroupId, inputName)
            } else {
                onConfirm(selectedGroupId, null)
            }
            dismiss()
        }
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
            layoutParams.height = (screenHeight * 0.8).toInt()
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