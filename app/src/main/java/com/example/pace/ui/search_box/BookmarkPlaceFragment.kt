package com.example.pace.ui.search_box

import android.os.Bundle
import android.util.Log
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
import com.example.pace.ui.search_box.DeleteConfirmDialogFragment
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

        groupViewModel.isOperationSuccess.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                val addDialog = parentFragmentManager.findFragmentByTag("AddGroupDialog") as? AddGroupDialogFragment
                addDialog?.dismiss()

                val editDialog = parentFragmentManager.findFragmentByTag("EditGroupDialog") as? AddGroupDialogFragment
                editDialog?.dismiss()
            }
        }


        groupViewModel.errorCode.observe(viewLifecycleOwner) { code ->
            Log.d("BookmarkFragment", "Error Code Received: $code")
            if (code == "PLACE_GROUP400_1") {
                val addDialog = parentFragmentManager.findFragmentByTag("AddGroupDialog") as? AddGroupDialogFragment
                val editDialog = parentFragmentManager.findFragmentByTag("EditGroupDialog") as? AddGroupDialogFragment


                addDialog?.showDuplicateError()
                editDialog?.showDuplicateError()
            }
        }

        groupViewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
//        val touchHelper = CommonSwipeTouchHelper(clampWidthDp = 120)
//        val itemTouchHelper = ItemTouchHelper(touchHelper)

        groupAdapter = BookmarkGroupAdapter(
            onGroupClick = { groupItem ->
                val bottomSheet = GroupDetailBottomSheet(groupItem)
                bottomSheet.show(parentFragmentManager, "GroupDetailBottomSheet")
            },
            onEditClick = { groupItem ->
                val dialog = AddGroupDialogFragment(groupItem) { reqest ->
                    groupViewModel.updateGroup(groupItem.groupId, reqest.groupName, reqest.groupColor)
                }
                dialog.show(parentFragmentManager, "EditGroupDialog")
            },
            onDeleteClick = { groupItem ->
                val message = "저장된 그룹 내 장소를/모두 삭제하시겠습니까?"
                val dialog = DeleteConfirmDialogFragment(message) {
                    groupViewModel.deleteGroup(groupItem.groupId)
                }
                dialog.show(parentFragmentManager, "DeleteConfirmDialog")
            },
            onAddClick = {
                val dialog = AddGroupDialogFragment { request ->
                    groupViewModel.createGroup(request.groupName, request.groupColor)
                }
                dialog.show(parentFragmentManager, "AddGroupDialog")
            }
        )
        val touchHelper = CommonSwipeTouchHelper(
            adapter = groupAdapter,
            clampWidthDp = 120
        )
        val itemTouchHelper = ItemTouchHelper(touchHelper)

        groupAdapter.setHelper(touchHelper)

        binding.rvBookmarkGroups.apply {
            adapter = groupAdapter
            layoutManager = LinearLayoutManager(requireContext())

            itemTouchHelper.attachToRecyclerView(this)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        touchHelper.closeAllMenus(this@apply)
                    }
                }
            })
        }

        updateAdapter()
    }

    private fun updateAdapter() {
        groupAdapter.submitList(currentGroupList.toList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}