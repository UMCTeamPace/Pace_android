package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.databinding.FragmentSearchRecommendBinding
import com.example.pace.ui.main.MainActivity

class SearchRecommendFragment : Fragment() {
    private var _binding: FragmentSearchRecommendBinding? = null
    private val binding get() = _binding!!

    private lateinit var resultAdapter: SearchRecommendAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSearchRecommendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        resultAdapter = SearchRecommendAdapter(emptyList()) { item ->
            (activity as? MainActivity)?.onRecommendItemClick(item)
        }

        binding.searchResultRv.apply {
            adapter = resultAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    fun updateList(newItems: List<SearchItem>) {
        if (_binding != null && ::resultAdapter.isInitialized) {
            resultAdapter.submitList(newItems)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}