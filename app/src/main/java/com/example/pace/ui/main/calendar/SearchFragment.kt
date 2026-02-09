package com.example.pace.ui.main.calendar

import SearchAdapter
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.FragmentSearchBinding
import com.example.pace.databinding.LayoutSearchEmptyBinding // EmptyView 바인딩 가정
import com.example.pace.ui.main.MainActivity
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    // 어댑터 선언
    private lateinit var searchAdapter: SearchAdapter

    // 결과 리스트용 리사이클러뷰 (동적 생성)
    private var recyclerView: RecyclerView? = null

    private val viewModel: ScheduleViewModel by lazy {
        (requireActivity() as MainActivity).getSharedViewModel()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()   // 리사이클러뷰 초기화
        setupSearchInput()    // 입력 리스너 설정
        setupButtons()        // 버튼 리스너 설정
        observeSearchResults() // 데이터 관찰

        // 초기 화면 설정 (첫 번째 사진)
        showInitialState()

        // 키보드 자동 올리기
        binding.etSearch.requestFocus()
        showKeyboard()

        // 툴바 제어
        val mainActivity = requireActivity() as MainActivity
        mainActivity.binding.mainToolbar.visibility = View.GONE
    }

    // SearchFragment.kt 수정 제안
    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter()
        // 1. RecyclerView를 미리 Container에 담아두고 visibility만 조절하는 게 성능상 좋습니다.
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
        }
    }

    private fun showResultList(results: List<Schedule>) {
        binding.searchResultContainer.removeAllViews() // 초기화
        recyclerView?.let { rv ->
            if (rv.parent == null) { // 부모가 없을 때만 add
                binding.searchResultContainer.addView(rv)
            } else {
                // 이미 추가되어 있다면 부모에서 떼었다가 다시 붙이거나,
                // 그냥 둠 (여기서는 removeAllViews를 했으니 다시 붙여야 함)
                binding.searchResultContainer.addView(rv)
            }
            searchAdapter.submitList(results)
        }
    }

    private fun setupSearchInput() {
        binding.etSearch.addTextChangedListener { text ->
            // 1. 입력된 텍스트 원본과 공백 제거본 로그 찍기
            val originalText = text?.toString() ?: ""
            val query = originalText.trim()
            searchAdapter.updateQuery(query)
            viewModel.searchSchedules(query)
            android.util.Log.d("SearchFlow", "-------------------------------")
            android.util.Log.d("SearchFlow", "입력 감지: '$originalText' (길이: ${originalText.length})")
            android.util.Log.d("SearchFlow", "Trim된 쿼리: '$query' (길이: ${query.length})")

            if (query.isEmpty()) {
                android.util.Log.d("SearchFlow", "결과: 쿼리가 비어있음 -> 초기화 실행")

                searchAdapter.updateQuery("")
                searchAdapter.submitList(emptyList())
                viewModel.clearSearch()
                showInitialState()
                return@addTextChangedListener
            }

            android.util.Log.d("SearchFlow", "결과: '$query' 검색 시도")
            searchAdapter.updateQuery(query)
            viewModel.searchSchedules(query)
        }
    }

    private fun setupButtons() {
        // 뒤로가기
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 필터 버튼 (XML의 id가 ivList 또는 btnFilter인 경우에 맞춰 수정)
        binding.ivList.setOnClickListener {
            val filterSheet = SearchFilterBottomSheet()
            filterSheet.show(childFragmentManager, "filter")
        }
    }

    private fun observeSearchResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collect { results ->
                // [중요] query가 비었는지 체크해서 return하는 코드를 반드시 지우세요!
                // 빈 리스트(results)가 들어오면 그대로 어댑터에 넘겨줘야 화면이 지워집니다.

                if (results.isEmpty()) {
                    val currentQuery = binding.etSearch.text.toString().trim()
                    if (currentQuery.isEmpty()) {
                        showInitialState() // 여기서 리스트가 비워짐
                    } else {
                        showEmptyState()   // "검색 결과가 없습니다" 표시
                    }
                } else {
                    showResultList(results)
                }
            }
        }
    }

    private fun showInitialState() {
        binding.searchResultContainer.removeAllViews()
        // [추가] 어댑터의 리스트도 비워줘야 잔상이 사라집니다.
        searchAdapter.submitList(emptyList())
        searchAdapter.updateQuery("")
    }


    private fun updateUI(results: List<Schedule>) {
        if (results.isEmpty()) {
            showEmptyState()
        } else {
            showResultList(results) // showResults 대신 이미 정의된 showResultList 사용
        }
    }

    // 2. showEmptyState 내의 바인딩 ID 참조 수정
    private fun showEmptyState() {
        binding.searchResultContainer.removeAllViews()

        // LayoutSearchEmptyBinding 사용
        val emptyBinding = LayoutSearchEmptyBinding.inflate(layoutInflater, binding.searchResultContainer, true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchRangeText.collect { range ->
                // tv_empty_range(XML) -> tvEmptyRange(Binding)
                emptyBinding.tvEmptyRange.text = range
            }
        }

        emptyBinding.btnExpandSearch.setOnClickListener {
            viewModel.expandSearchRange()
        }
    }

    private fun showKeyboard() {
        binding.etSearch.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as MainActivity).binding.mainToolbar.visibility = View.VISIBLE
        _binding = null
    }
}