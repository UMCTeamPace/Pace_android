package com.example.pace.ui.search_box.group

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
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
    private var shouldRefreshOnResume = false

    companion object {
        const val SAVED_PLACES_CHANGED_REQUEST_KEY = "savedPlacesChanged"
        const val SAVED_PLACES_CHANGED_GROUP_ID_KEY = "groupId"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvGroupTitle.text = groupItem.groupName

        binding.tvFilterText.text = "최신 등록순"

        setupRecyclerView()
        observeViewModel()
        setupListeners()

        groupViewModel.getSavedPlaces(groupItem.groupId)

    }

    override fun onResume() {
        super.onResume()
        if (shouldRefreshOnResume) {
            shouldRefreshOnResume = false
            groupViewModel.getSavedPlaces(groupItem.groupId, groupViewModel.currentSortType)
            setFragmentResult(
                SAVED_PLACES_CHANGED_REQUEST_KEY,
                bundleOf(SAVED_PLACES_CHANGED_GROUP_ID_KEY to groupItem.groupId)
            )
        }
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
            sheet.translationY = 0f
            val displayMetrics = resources.displayMetrics
            val sheetHeight = (displayMetrics.heightPixels * 663f / 800f).toInt()
            val layoutParams = sheet.layoutParams
            layoutParams.height = sheetHeight
            sheet.layoutParams = layoutParams

            val behavior = BottomSheetBehavior.from(sheet)

            behavior.isHideable = true
            behavior.skipCollapsed = true
            behavior.peekHeight = sheetHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setupHandleDrag(sheet, behavior, sheetHeight)
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
            shouldRefreshOnResume = true

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

    private fun setupHandleDrag(
        sheet: View,
        behavior: BottomSheetBehavior<View>,
        sheetHeight: Int
    ) {
        var downY = 0f
        var startTranslationY = 0f

        binding.layoutDragHandleArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sheet.animate().cancel()
                    downY = event.rawY
                    startTranslationY = sheet.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dragOffset = (startTranslationY + event.rawY - downY).coerceAtLeast(0f)
                    sheet.translationY = dragOffset
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val measuredSheetHeight = sheet.height.takeIf { it > 0 } ?: sheetHeight
                    val closeThreshold = measuredSheetHeight * 0.1f
                    val shouldClose = sheet.translationY >= closeThreshold
                    if (shouldClose) {
                        sheet.animate()
                            .translationY(sheet.height.toFloat())
                            .setDuration(180L)
                            .withEndAction {
                                behavior.state = BottomSheetBehavior.STATE_HIDDEN
                            }
                            .start()
                    } else {
                        sheet.animate()
                            .translationY(0f)
                            .setDuration(180L)
                            .start()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun showFilterDialog() {
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
        updateSortOptionStyles(dialogView, rgSortOptions.checkedRadioButtonId)

        rgSortOptions.setOnCheckedChangeListener { _, checkedId ->
            updateSortOptionStyles(dialogView, checkedId)
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

    private fun updateSortOptionStyles(dialogView: View, checkedId: Int) {
        val selectedTypeface = ResourcesCompat.getFont(requireContext(), R.font.pretendard_semibold)
        val defaultTypeface = ResourcesCompat.getFont(requireContext(), R.font.pretendard_regular)
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val defaultColor = ContextCompat.getColor(requireContext(), R.color.text_tertiary)

        listOf(R.id.rb_latest, R.id.rb_oldest, R.id.rb_name).forEach { id ->
            val radioButton = dialogView.findViewById<RadioButton>(id)
            val isSelected = id == checkedId
            radioButton.typeface = if (isSelected) {
                selectedTypeface ?: Typeface.DEFAULT_BOLD
            } else {
                defaultTypeface ?: Typeface.DEFAULT
            }
            radioButton.setTextColor(if (isSelected) selectedColor else defaultColor)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun findRouteFragmentRecursively(fragment: androidx.fragment.app.Fragment?): RouteFragment? {
        if (fragment == null) return null

        if (fragment.parentFragment is RouteFragment) {
            return fragment.parentFragment as RouteFragment
        }

        return findRouteFragmentRecursively(fragment.parentFragment)
    }
}
