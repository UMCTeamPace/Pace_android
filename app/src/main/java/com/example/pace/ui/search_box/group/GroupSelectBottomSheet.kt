package com.example.pace.ui.search_box.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.pace.data.model.response.GroupItem
import com.example.pace.databinding.BottomSheetGroupSelectBinding
import com.example.pace.ui.search_box.AddGroupDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GroupSelectBottomSheet(
    private val mode: Mode,
    private val placeName: String? = null, // 저장 모드일 때만 사용될 장소명
    private val onConfirm: (Long, String?) -> Unit // (groupId, changedName)
) : BottomSheetDialogFragment() {

    enum class Mode { SAVE, MOVE }

    private var _binding: BottomSheetGroupSelectBinding? = null
    private val binding get() = _binding!!
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
    }

    private fun setupUI() {
        if (mode == Mode.MOVE) {
            binding.tvSheetTitle.text = "이동"
            binding.layoutInputContainer.visibility = View.GONE
            binding.btnSave.text = "이동"
        } else {
            binding.tvSheetTitle.text = placeName ?: "장소 저장"
            binding.layoutInputContainer.visibility = View.VISIBLE
            binding.etPlaceMemo.setText(placeName)
            binding.btnSave.text = "저장"
        }
    }

    private fun setupRecyclerView() {
        groupList.clear()
        groupList.addAll(listOf(
            GroupItem(1, "내 장소", "#FFA500", "", 0),
            GroupItem(2, "식당", "#FF5252", "", 0),
            GroupItem(3, "멘션-숙소", "#FF4081", "", 0),
            GroupItem(4, "서점-기타", "#40C4FF", "", 0),
            GroupItem(5, "주점", "#7C4DFF", "", 0),
            GroupItem(6, "카페", "#69F0AE", "", 0)
        ))

        radioAdapter = GroupRadioAdapter(groupList) {
            // [+ 새 그룹 추가] 클릭 시 다이얼로그 띄우기
            val dialog = AddGroupDialogFragment { request ->
                // request는 CreateGroupRequest(groupName, groupColor) 타입

                val newGroup = GroupItem(
                    groupId = System.currentTimeMillis(),
                    groupName = request.groupName,
                    groupColor = request.groupColor,
                    createdAt = "",
                    placeCount = 0
                )

                groupList.add(newGroup)
                radioAdapter.updateItems(groupList)

                binding.rvGroupList.smoothScrollToPosition(groupList.size - 1)

                Toast.makeText(requireContext(), "${request.groupName} 그룹 추가됨", Toast.LENGTH_SHORT).show()
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

            // 저장 모드일 때만 텍스트값 가져옴 (이동 모드면 null)
            val finalName = if (mode == Mode.SAVE) binding.etPlaceMemo.text.toString() else null

            onConfirm(selectedGroupId, finalName)
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