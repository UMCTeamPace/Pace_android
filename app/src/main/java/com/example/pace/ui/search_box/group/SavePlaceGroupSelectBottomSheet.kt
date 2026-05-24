package com.example.pace.ui.search_box.group

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
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
class SavePlaceGroupSelectBottomSheet(
    private val placeName: String,
    private val placeId: String
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGroupSelectBinding? = null
    private val binding get() = _binding!!
    private val groupViewModel: GroupViewModel by activityViewModels()
    private lateinit var checkboxAdapter: GroupCheckboxAdapter
    private val groupList = mutableListOf<GroupItem>()
    private var initialSavedPlaceIdsByGroupId: Map<Long, Long> = emptyMap()
    private var hasRequestedSavedGroups = false
    private var isInitialSelectionLoaded = false

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
        setupKeyboardDismissListeners()
        observeViewModel()

        groupViewModel.fetchGroupList()
    }

    private fun setupRecyclerView() {
        checkboxAdapter = GroupCheckboxAdapter(
            items = groupList,
            onAddClick = {
                val dialog = AddGroupDialogFragment { request ->
                    groupViewModel.createGroup(request.groupName, request.groupColor)
                }
                dialog.show(parentFragmentManager, "AddGroupDialog")
            },
            onSelectionChanged = {
                binding.tvErrorMsg.visibility = View.GONE
                updateSaveButtonState()
            }
        )

        binding.rvGroupList.adapter = checkboxAdapter
        checkboxAdapter.setInteractionEnabled(false)
    }

    private fun setupUI() {
        binding.btnSave.isEnabled = false
        binding.tvErrorMsg.visibility = View.GONE
        binding.tvSheetTitle.text = placeName
        binding.layoutInputContainer.visibility = View.VISIBLE
        binding.etPlaceMemo.setText("")
        binding.etPlaceMemo.hint = placeName
        updateSaveButtonState()
    }

    private fun setupListeners() {
        binding.etPlaceMemo.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvErrorMsg.visibility = View.GONE
                updateSaveButtonState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnCancel.setOnClickListener {
            hideKeyboardAndClearFocus()
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            hideKeyboardAndClearFocus()
            binding.tvErrorMsg.visibility = View.GONE
            val selectedGroupIds = checkboxAdapter.getSelectedGroupIds()
            val initialGroupIds = initialSavedPlaceIdsByGroupId.keys
            val inputName = binding.etPlaceMemo.text.toString().trim()
            if (!isInitialSelectionLoaded) return@setOnClickListener

            val groupIdsToAdd = selectedGroupIds
                .filterNot { initialGroupIds.contains(it) }
            val savedPlaceIdsToDelete = initialGroupIds
                .filterNot { selectedGroupIds.contains(it) }
                .mapNotNull { initialSavedPlaceIdsByGroupId[it] }

            if (groupIdsToAdd.isEmpty() && savedPlaceIdsToDelete.isEmpty()) return@setOnClickListener
            if (groupIdsToAdd.isNotEmpty() && inputName.isEmpty()) return@setOnClickListener

            checkboxAdapter.setInteractionEnabled(false)
            binding.btnSave.isEnabled = false
            groupViewModel.updatePlaceGroups(
                placeId = placeId,
                placeName = inputName.ifEmpty { placeName },
                groupIdsToAdd = groupIdsToAdd,
                savedPlaceIdsToDelete = savedPlaceIdsToDelete
            )
        }
    }

    private fun setupKeyboardDismissListeners() {
        val dismissOnTouch = View.OnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                hideKeyboardAndClearFocus()
            }
            false
        }

        binding.root.setOnTouchListener(dismissOnTouch)
        binding.tvSheetTitle.setOnTouchListener(dismissOnTouch)
        binding.layoutButtons.setOnTouchListener(dismissOnTouch)

        binding.rvGroupList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    hideKeyboardAndClearFocus()
                }
                return false
            }
        })
    }

    private fun hideKeyboardAndClearFocus() {
        binding.etPlaceMemo.clearFocus()
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.etPlaceMemo.windowToken, 0)
    }

    private fun observeViewModel() {
        groupViewModel.groupList.observe(viewLifecycleOwner) { groups ->
            groupList.clear()
            groupList.addAll(groups)

            if (::checkboxAdapter.isInitialized) {
                checkboxAdapter.updateItems(groupList)
            }

            if (!hasRequestedSavedGroups) {
                hasRequestedSavedGroups = true
                if (groupList.isEmpty()) {
                    isInitialSelectionLoaded = true
                    checkboxAdapter.setInteractionEnabled(true)
                    updateSaveButtonState()
                } else {
                    groupViewModel.fetchSavedGroupsForPlace(placeId, groupList)
                }
            }
        }

        groupViewModel.placeSavedStatesByPlaceId.observe(viewLifecycleOwner) { statesByPlaceId ->
            if (!hasRequestedSavedGroups) return@observe
            if (!statesByPlaceId.containsKey(placeId)) return@observe
            val savedPlaceIdsByGroupId = statesByPlaceId[placeId]
                .orEmpty()
                .associate { it.groupId to it.savedPlaceId }
            initialSavedPlaceIdsByGroupId = savedPlaceIdsByGroupId
            isInitialSelectionLoaded = true
            checkboxAdapter.setSelectedGroupIds(savedPlaceIdsByGroupId.keys)
            checkboxAdapter.setInteractionEnabled(true)
            updateSaveButtonState()
        }

        groupViewModel.isPlaceSavedGroupLoading.observe(viewLifecycleOwner) { isLoading ->
            val isEditLoading = groupViewModel.isPlaceGroupEditLoading.value == true
            checkboxAdapter.setInteractionEnabled(!isLoading && !isEditLoading && isInitialSelectionLoaded)
            updateSaveButtonState()
        }

        groupViewModel.isPlaceGroupEditLoading.observe(viewLifecycleOwner) { isLoading ->
            val isSelectionLoading = groupViewModel.isPlaceSavedGroupLoading.value == true
            checkboxAdapter.setInteractionEnabled(!isLoading && !isSelectionLoading && isInitialSelectionLoaded)
            updateSaveButtonState()
        }

        groupViewModel.errorCode.observe(viewLifecycleOwner) { code ->
            if (code == "PLACE400_1") {
                binding.tvErrorMsg.visibility = View.VISIBLE
                updateSaveButtonState()
            } else {
                binding.tvErrorMsg.visibility = View.GONE
            }
        }

        groupViewModel.isPlaceGroupEditSuccess.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                dismiss()
            }
        }

        groupViewModel.isOperationSuccess.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                val dialog = parentFragmentManager.findFragmentByTag("AddGroupDialog") as? AddGroupDialogFragment
                dialog?.dismiss()
            }
        }
    }

    private fun updateSaveButtonState() {
        val currentGroupIds = checkboxAdapter.getSelectedGroupIds().toSet()
        val initialGroupIds = initialSavedPlaceIdsByGroupId.keys
        val hasChanges = currentGroupIds != initialGroupIds
        val hasGroupsToAdd = currentGroupIds.any { !initialGroupIds.contains(it) }
        val hasRequiredName = !hasGroupsToAdd ||
            binding.etPlaceMemo.text.toString().trim().isNotEmpty()
        val isLoading = groupViewModel.isPlaceSavedGroupLoading.value == true ||
            groupViewModel.isPlaceGroupEditLoading.value == true
        val isStateValid = isInitialSelectionLoaded &&
            !isLoading &&
            hasChanges &&
            hasRequiredName

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
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let { sheet ->
            val screenHeight = resources.displayMetrics.heightPixels
            val layoutParams = sheet.layoutParams
            layoutParams.height = (screenHeight * (626f / 800f)).toInt()
            sheet.layoutParams = layoutParams

            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isHideable = true
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            setupHandleSwipe(behavior)
        }
    }

    private fun setupHandleSwipe(behavior: BottomSheetBehavior<View>) {
        val closeThreshold = ViewConfiguration.get(requireContext()).scaledTouchSlop * 2
        var downY = 0f
        var isClosing = false

        binding.viewHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    hideKeyboardAndClearFocus()
                    downY = event.rawY
                    isClosing = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - downY
                    if (!isClosing && deltaY > closeThreshold) {
                        isClosing = true
                        behavior.state = BottomSheetBehavior.STATE_HIDDEN
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isClosing = false
                    true
                }
                else -> true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
