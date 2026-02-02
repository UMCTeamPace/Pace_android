package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.data.db.SearchDatabase
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.repository.SearchRepository
import com.example.pace.databinding.FragmentRecentSearchBinding
import kotlinx.coroutines.launch

class RecentSearchFragment : Fragment() {
    private var _binding: FragmentRecentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: SearchRepository
    private lateinit var historyAdapter: RecentHistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // DB 및 Repository 초기화
        val dao = SearchDatabase.getDatabase(requireContext()).searchDao()
        repository = SearchRepository(dao)

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        // 어댑터 생성 (삭제 버튼은 아직 없으므로 빈 람다 전달)
        historyAdapter = RecentHistoryAdapter(
            onItemClick = { item ->
                // 클릭 시 메인 검색창에 검색어 입력 로직 호출 (필요시 RouteFragment와 연동)
            },
            onDeleteClick = { /* 삭제 로직 미구현 */ }
        )
        binding.rvRecentSearch.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.allHistory.collect { historyList ->
                    historyAdapter.submitList(historyList) // DB 변경 시 자동 호출됨
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}