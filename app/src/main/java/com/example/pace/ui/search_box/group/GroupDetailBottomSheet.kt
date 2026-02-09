package com.example.pace.ui.search_box.group

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.data.model.response.GroupItem
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.databinding.BottomSheetGroupDetailBinding
import com.example.pace.ui.main.route.RouteFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GroupDetailBottomSheet(
    private val groupItem: GroupItem
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGroupDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var placeAdapter: GroupPlaceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvGroupTitle.text = groupItem.groupName

        // 더미 장소 데이터
        val placeList = mutableListOf(
            SavePlaceResponse(1, 1, "스타벅스 사당점", "ChIJ77yqsE6gfDUR7jztozyfVMo", "CreatedAt"),
            SavePlaceResponse(2, 1, "맥도날드 이수점", "ChIJ77yqsE6gfDUR7jztozyfVMo", "2026-02-02 11:52")
        )

        // 어댑터 연결
        placeAdapter = GroupPlaceAdapter(placeList) { place ->
            val id = place.placeId

            if (id.isNotEmpty()) {
                // 부모 찾기 시도
                val routeFragment = findRouteFragmentRecursively(this)

                if (routeFragment != null) {
                    routeFragment.onSavedPlaceClick(id)
                    dismiss()
                }
            }
        }

        binding.rvGroupPlaces.apply {
            adapter = placeAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        binding.tvEditMode.setOnClickListener {
            // todo 편집 액티비티 띄우기
            val intent = Intent(requireContext(), GroupEditActivity::class.java).apply {
                putExtra("GROUP_ID", groupItem.groupId)
                putExtra("GROUP_NAME", groupItem.groupName)
                putParcelableArrayListExtra("PLACE_LIST", ArrayList(placeList))
            }
            startActivity(intent)
        }

        // 필터 버튼
        binding.layoutFilter.setOnClickListener {
            Toast.makeText(context, "필터 다이얼로그 띄우기", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let { sheet ->
            val layoutParams = sheet.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            sheet.layoutParams = layoutParams

            val behavior = BottomSheetBehavior.from(sheet)

            behavior.isHideable = false

            val displayMetrics = resources.displayMetrics
            behavior.peekHeight = (displayMetrics.heightPixels * 0.6).toInt()

            behavior.state = BottomSheetBehavior.STATE_COLLAPSED

            behavior.isDraggable = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun findRouteFragmentRecursively(fragment: androidx.fragment.app.Fragment?): com.example.pace.ui.main.route.RouteFragment? {
        if (fragment == null) return null

        // 1. 내 부모가 RouteFragment인가?
        if (fragment.parentFragment is com.example.pace.ui.main.route.RouteFragment) {
            return fragment.parentFragment as com.example.pace.ui.main.route.RouteFragment
        }

        // 2. 아니면 부모의 부모를 찾아보자 (재귀 호출)
        return findRouteFragmentRecursively(fragment.parentFragment)
    }
}