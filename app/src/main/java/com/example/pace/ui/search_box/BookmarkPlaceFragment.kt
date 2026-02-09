package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.response.GroupItem
import com.example.pace.data.viewmodel.GroupViewModel
import com.example.pace.databinding.FragmentBookmarkPlaceBinding
import com.example.pace.ui.search_box.group.GroupDetailBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class BookmarkPlaceFragment : Fragment(){

    private var _binding: FragmentBookmarkPlaceBinding? = null
    private val binding get() = _binding!!
    private val groupViewModel: GroupViewModel by viewModels()
    private lateinit var groupAdapter: BookmarkGroupAdapter
    private val currentGroupList = mutableListOf<GroupItem>()
    private var dummyIdCounter: Long = 100

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookmarkPlaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        groupViewModel.fetchGroupList()
    }

    private fun observeViewModel() {
        // 그룹 리스트 관찰
        groupViewModel.groupList.observe(viewLifecycleOwner) { groups ->
            currentGroupList.clear()
            currentGroupList.addAll(groups)
            groupAdapter.submitList(currentGroupList.toList())
        }

        // 에러 메시지 관찰
        groupViewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        val touchHelper = CommonSwipeTouchHelper(clampWidthDp = 120)
        val itemTouchHelper = ItemTouchHelper(touchHelper)

        groupAdapter = BookmarkGroupAdapter(
            onGroupClick = { groupItem ->
                val bottomSheet = GroupDetailBottomSheet(groupItem)
                bottomSheet.show(parentFragmentManager, "GroupDetailBottomSheet")
            },
            onEditClick = { groupItem ->
//                Toast.makeText(context, "${groupItem.groupName} 수정", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = {
                groupItem ->
            },
            onAddClick = {
                val dialog = AddGroupDialogFragment { request ->
                    groupViewModel.createGroup(request.groupName, request.groupColor)
                }
                dialog.show(parentFragmentManager, "AddGroupDialog")
            }
        )

        groupAdapter.setHelper(touchHelper)

        binding.rvBookmarkGroups.apply {
            adapter = groupAdapter
            layoutManager = LinearLayoutManager(requireContext())

            itemTouchHelper.attachToRecyclerView(this)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        touchHelper.closeSwipedMenu()
                    }
                }
            })
        }

        updateAdapter()
    }

    private fun addNewDummyGroup(name: String, color: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())

        val newGroup = GroupItem(
            groupId = dummyIdCounter++,
            groupName = name,
            groupColor = color,
            createdAt = currentDate,
            placeCount = 0
        )

        currentGroupList.add(newGroup)
        updateAdapter() // 화면 갱신

        // 추가된 아이템 위치로 스크롤
        binding.rvBookmarkGroups.smoothScrollToPosition(currentGroupList.size - 1)
    }

    private fun updateAdapter() {
        groupAdapter.submitList(currentGroupList.toList())
    }

    private fun loadInitialData() {
        currentGroupList.clear()
        currentGroupList.add(GroupItem(1, "카페", "#FFA500", "2024-02-09", 3))
        currentGroupList.add(GroupItem(2, "맛집 리스트", "#FF5252", "2024-02-09", 10))
        currentGroupList.add(GroupItem(3, "식당", "#3F51B5", "2024-02-09", 5))
        currentGroupList.add(GroupItem(4, "데이트 코스", "#4CAF50", "2024-02-09", 0))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}