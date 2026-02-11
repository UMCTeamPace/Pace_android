package com.example.pace.ui.search_box.group

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.data.model.response.GroupItem
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.data.viewmodel.GroupViewModel
import com.example.pace.databinding.BottomSheetGroupDetailBinding
import com.example.pace.ui.main.route.RouteFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupDetailBottomSheet(
    private val groupItem: GroupItem
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGroupDetailBinding? = null
    private val binding get() = _binding!!
    private val groupViewModel: GroupViewModel by viewModels()
    private lateinit var placeAdapter: GroupPlaceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvGroupTitle.text = groupItem.groupName

        binding.tvFilterText.text = "최신 등록순"

        groupViewModel.getSavedPlaces(groupItem.groupId)

        setupRecyclerView()
        observeViewModel()
        setupListeners()

        groupViewModel.getSavedPlaces(groupItem.groupId)

    }

    override fun onResume() {
        super.onResume()
        groupViewModel.getSavedPlaces(groupItem.groupId, groupViewModel.currentSortType)
    }

    private fun observeViewModel() {
        groupViewModel.savedPlaces.observe(viewLifecycleOwner) { list ->
            android.util.Log.d("DEBUG_LIST", "받은 데이터 개수: ${list?.size}")
            placeAdapter.updateItems(list)
        }

        groupViewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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

    private fun setupRecyclerView() {
        placeAdapter = GroupPlaceAdapter(emptyList()) { place ->
            val id = place.placeId
            if (id.isNotEmpty()) {
                val routeFragment = findRouteFragmentRecursively(this)
                routeFragment?.onSavedPlaceClick(id)
                dismiss()
            }
        }

        binding.rvGroupPlaces.apply {
            adapter = placeAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupListeners() {
        // [편집 버튼]
        binding.tvEditMode.setOnClickListener {
            val currentList = groupViewModel.savedPlaces.value ?: emptyList()

            val intent = Intent(requireContext(), GroupEditActivity::class.java).apply {
                putExtra("GROUP_ID", groupItem.groupId)
                putExtra("GROUP_NAME", groupItem.groupName)
                putParcelableArrayListExtra("PLACE_LIST", ArrayList(currentList))
            }
            startActivity(intent)
        }

        binding.layoutFilter.setOnClickListener {
            showFilterDialog() //todo
        }
    }

    private fun showFilterDialog() {
        // [수정] 만드신 XML 파일명(dialog_place_filter) 사용
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_place_filter, null)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val rgSortOptions = dialogView.findViewById<RadioGroup>(R.id.rg_sort_options_group)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_search_filter)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_save_search_filter)

        when (groupViewModel.currentSortType) {
            "LATEST" -> rgSortOptions.check(R.id.rb_latest)
            "OLDEST" -> rgSortOptions.check(R.id.rb_oldest)
            "NAME" -> rgSortOptions.check(R.id.rb_name)
            else -> rgSortOptions.check(R.id.rb_latest)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val selectedId = rgSortOptions.checkedRadioButtonId

            val (apiSortType, uiText) = when (selectedId) {
                R.id.rb_latest -> "LATEST" to "최신 등록순"
                R.id.rb_oldest -> "OLDEST" to "오래된 등록순"
                R.id.rb_name -> "NAME" to "장소명순"
                else -> "LATEST" to "최신 등록순"
            }

            binding.tvFilterText.text = uiText

            groupViewModel.getSavedPlaces(groupItem.groupId, apiSortType)

            dialog.dismiss()
        }

        dialog.show()

        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
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