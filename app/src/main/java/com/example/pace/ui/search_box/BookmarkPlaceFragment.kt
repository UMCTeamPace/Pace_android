package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.data.model.response.GroupItem
import com.example.pace.databinding.FragmentBookmarkPlaceBinding

class BookmarkPlaceFragment : Fragment(){

    private var _binding: FragmentBookmarkPlaceBinding? = null
    private val binding get() = _binding!!
    private lateinit var groupAdapter: BookmarkGroupAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookmarkPlaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        groupAdapter = BookmarkGroupAdapter(
            onGroupClick = { groupItem ->
                // 그룹 아이템 클릭 시 (나중에 바텀시트 로직 추가)
            },
            onAddClick = {
                // "새 그룹 추가" 아이템 클릭 시 다이얼로그 띄우기
//                showAddGroupDialog()
            }
        )

        binding.rvBookmarkGroups.apply {
            adapter = groupAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        }

//        binding.btnAddNewGroup.setOnClickListener {
//            // 다이얼로그 띄우기 (나중에 구현)
//        }

        val testData = listOf(GroupItem(1, "식당", "#FFA500", "...", 15))
        groupAdapter.submitList(testData)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}