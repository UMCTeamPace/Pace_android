package com.example.pace.ui.search_box

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.data.model.response.GroupItem
import com.example.pace.data.viewmodel.GroupViewModel
import com.example.pace.databinding.FragmentBookmarkPlaceBinding
import com.example.pace.ui.main.route.RouteFragment
import com.example.pace.ui.search_box.group.GroupDetailBottomSheet
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BookmarkPlaceFragment : Fragment() {

    private var _binding: FragmentBookmarkPlaceBinding? = null
    private val binding get() = _binding!!
    private val groupViewModel: GroupViewModel by viewModels()
    private val sharedGroupViewModel: GroupViewModel by activityViewModels()
    private lateinit var groupAdapter: BookmarkGroupAdapter
    private val currentGroupList = mutableListOf<GroupItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookmarkPlaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFragmentResultListeners()
        observeViewModel()
        groupViewModel.fetchGroupList()
    }

    private fun setupFragmentResultListeners() {
        parentFragmentManager.setFragmentResultListener(
            GroupDetailBottomSheet.SAVED_PLACES_CHANGED_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            sharedGroupViewModel.clearPlaceSavedStateCache()
            groupViewModel.fetchGroupList()
        }
    }

    private fun observeViewModel() {
        groupViewModel.groupList.observe(viewLifecycleOwner) { groups ->
            currentGroupList.clear()
            currentGroupList.addAll(groups)
            groupAdapter.submitList(currentGroupList.toList())
        }

        groupViewModel.isOperationSuccess.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                sharedGroupViewModel.clearPlaceSavedStateCache()
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
        groupAdapter = BookmarkGroupAdapter(
            onGroupClick = { groupItem ->
                GroupDetailBottomSheet(groupItem).show(parentFragmentManager, "GroupDetailBottomSheet")
            },
            onEditClick = { groupItem ->
                val dialog = AddGroupDialogFragment(groupItem) { request ->
                    groupViewModel.updateGroup(groupItem.groupId, request.groupName, request.groupColor)
                }
                dialog.show(parentFragmentManager, "EditGroupDialog")
            },
            onDeleteClick = { groupItem ->
                val message = "저장된 그룹 내 장소를 \n 모두 삭제하시겠습니까?"
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
            },
            onSwipeStart = {
                (parentFragment?.parentFragment as? RouteFragment)?.dismissSearchInputFocus()
            }
        )

        binding.rvBookmarkGroups.apply {
            adapter = groupAdapter
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = null
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                    if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) {
                        (parentFragment?.parentFragment as? RouteFragment)?.dismissSearchInputFocus()
                    }
                }
            })
        }

        groupAdapter.submitList(currentGroupList.toList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
