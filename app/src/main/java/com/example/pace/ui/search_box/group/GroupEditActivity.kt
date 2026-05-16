package com.example.pace.ui.search_box.group

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.data.viewmodel.GroupViewModel
import com.example.pace.databinding.ActivityGroupEditBinding
import com.example.pace.ui.search_box.DeleteConfirmDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupEditBinding
    private lateinit var adapter: GroupEditPlaceAdapter
    private var placeList = mutableListOf<SavePlaceResponse>()
    private var isAllSelected = false

    private val groupViewModel: GroupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupEditBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.apply {
            statusBarColor = Color.WHITE
            navigationBarColor = Color.WHITE
            val decorView = window.decorView
            val controller = WindowCompat.getInsetsController(this, decorView)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }

        setContentView(binding.root)
        applySystemBarInsets()

        // 1. Intent 데이터 수신
        val groupId = intent.getLongExtra("GROUP_ID", -1L)
        val groupName = intent.getStringExtra("GROUP_NAME") ?: ""

        // 2. 리스트 수신 (Parcelable 대응)
        val receivedList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("PLACE_LIST", SavePlaceResponse::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<SavePlaceResponse>("PLACE_LIST")
        }

        receivedList?.let { placeList.addAll(it) }

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun applySystemBarInsets() {
        val rootBasePaddingLeft = binding.root.paddingLeft
        val rootBasePaddingTop = binding.root.paddingTop
        val rootBasePaddingRight = binding.root.paddingRight
        val rootBasePaddingBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.root.setPadding(
                rootBasePaddingLeft + systemBars.left,
                rootBasePaddingTop + systemBars.top,
                rootBasePaddingRight + systemBars.right,
                rootBasePaddingBottom + systemBars.bottom
            )

            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun observeViewModel() {
        // [중요] API 작업 성공 시 동작
        groupViewModel.isOperationSuccess.observe(this) { success ->
            if (success) {
                val sortedIndices = adapter.selectedPositions.sortedDescending()
                sortedIndices.forEach { index -> placeList.removeAt(index) }

                adapter.selectedPositions.clear()
                adapter.notifyDataSetChanged()

                isAllSelected = false
                updateSelectAllIcon()
                binding.tvEditTitle.text = "편집"
                finish()
            }
        }

        groupViewModel.errorMessage.observe(this) { msg ->
            val errorCode = groupViewModel.errorCode.value
            if (!msg.isNullOrBlank() && errorCode != "PLACE400_1") {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun setupRecyclerView() {
        adapter = GroupEditPlaceAdapter(placeList) { selectedCount ->
            // 상단 타이틀 업데이트
            binding.tvEditTitle.text = when {
                selectedCount == 0 -> "편집"
                selectedCount == placeList.size -> "전체 선택"
                else -> "${selectedCount}개 선택"
            }

            val isEnabled = selectedCount > 0
            binding.btnMovePlace.isEnabled = isEnabled
            binding.btnDeletePlace.isEnabled = isEnabled

            val deleteColor = if (isEnabled) {
                androidx.core.content.ContextCompat.getColor(this, R.color.semantic_warning)
            } else {
                androidx.core.content.ContextCompat.getColor(this, R.color.text_disabled)
            }
            binding.btnDeletePlace.backgroundTintList = android.content.res.ColorStateList.valueOf(deleteColor)

            // 전체 선택 상태 업데이트
            isAllSelected = (selectedCount == placeList.size && placeList.isNotEmpty())
            updateSelectAllIcon()
        }

        binding.rvEditPlaces.apply {
            adapter = this@GroupEditActivity.adapter
            layoutManager = LinearLayoutManager(this@GroupEditActivity)
        }
        binding.btnMovePlace.isEnabled = false
        binding.btnDeletePlace.isEnabled = false

        val disabledColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_disabled)
        binding.btnDeletePlace.backgroundTintList = android.content.res.ColorStateList.valueOf(disabledColor)
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { finish() }

        binding.ivSelectAll.setOnClickListener {
            isAllSelected = !isAllSelected
            adapter.toggleAllSelection(isAllSelected)
            updateSelectAllIcon()
        }

        binding.btnDeletePlace.setOnClickListener {
            showDeleteConfirmDialog(adapter.selectedPositions.size)
        }

        binding.btnMovePlace.setOnClickListener {
            showMoveBottomSheet()
        }
    }

    private fun updateSelectAllIcon() {
        // iv_select_all이 ImageView이므로 isSelected로 제어
        binding.ivSelectAll.isSelected = isAllSelected
    }

    private fun showDeleteConfirmDialog(count: Int) {
        val message = "총 ${count}곳의 장소를\n그룹에서 삭제하시겠습니까?"
        val dialog = DeleteConfirmDialogFragment(message) {
            val selectedIds = adapter.selectedPositions.map { placeList[it].savedPlaceId }
            groupViewModel.deletePlaces(selectedIds)
        }
        dialog.show(supportFragmentManager, "DeleteConfirmDialog")
    }

    private fun showMoveBottomSheet() {
        val selectedIds = adapter.selectedPositions.map { placeList[it].savedPlaceId }

        val bottomSheet = GroupSelectBottomSheet(
            mode = GroupSelectBottomSheet.Mode.MOVE,
            onConfirm = { targetGroupId, _ ->
                groupViewModel.movePlaces(selectedIds, targetGroupId)
            }
        )
        bottomSheet.show(supportFragmentManager, "GroupSelectBottomSheet")
    }
}
