package com.example.pace.ui.search_box.group

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.databinding.ActivityGroupEditBinding

class GroupEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupEditBinding
    private lateinit var adapter: GroupEditPlaceAdapter
    private var placeList = mutableListOf<SavePlaceResponse>()
    private var isAllSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        // 3. UI 초기화 및 리스너 설정
        setupRecyclerView()
        setupListeners()
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

            // 전체 선택 상태 업데이트 (개별 선택으로 전체가 찼을 때)
            isAllSelected = (selectedCount == placeList.size && placeList.isNotEmpty())
            updateSelectAllIcon()
        }

        binding.rvEditPlaces.apply {
            adapter = this@GroupEditActivity.adapter
            layoutManager = LinearLayoutManager(this@GroupEditActivity)
        }
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.show()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val layoutParams = android.view.WindowManager.LayoutParams()
        layoutParams.copyFrom(dialog.window?.attributes)

        layoutParams.width = (screenWidth * 0.75).toInt()
        layoutParams.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT

        dialog.window?.attributes = layoutParams

        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tv_delete_dialog_message)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btn_delete_cancel)
        val btnConfirm = dialogView.findViewById<android.widget.Button>(R.id.btn_delete_confirm)

        tvMessage.text = "총 ${count}곳의 장소를\n그룹에서 삭제하시겠습니까?"

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val sortedIndices = adapter.selectedPositions.sortedDescending()
            sortedIndices.forEach { index ->
                // TODO: 서버 삭제 API 호출 (placeList[index].savedPlaceId 사용)
                placeList.removeAt(index)
            }

            adapter.selectedPositions.clear()
            adapter.notifyDataSetChanged()

            isAllSelected = false
            updateSelectAllIcon()

            binding.tvEditTitle.text = "편집"
            binding.btnMovePlace.isEnabled = false
            binding.btnDeletePlace.isEnabled = false

            Toast.makeText(this, "${count}개의 장소가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showMoveBottomSheet() {
        val selectedPlaceIds = adapter.selectedPositions.map { placeList[it].savedPlaceId }

        val bottomSheet = GroupSelectBottomSheet(
            mode = GroupSelectBottomSheet.Mode.MOVE,
            onConfirm = { targetGroupId, _ ->
                // TODO: 서버 이동 API 호출
                Toast.makeText(this, "선택한 장소를 이동했습니다.", Toast.LENGTH_SHORT).show()

                val sortedIndices = adapter.selectedPositions.sortedDescending()
                sortedIndices.forEach { index -> placeList.removeAt(index) }
                adapter.selectedPositions.clear()
                adapter.notifyDataSetChanged()

                isAllSelected = false
                updateSelectAllIcon()
                binding.tvEditTitle.text = "편집"
                binding.btnMovePlace.isEnabled = false
                binding.btnDeletePlace.isEnabled = false
            }
        )
        bottomSheet.show(supportFragmentManager, "GroupSelectBottomSheet")
    }
}
