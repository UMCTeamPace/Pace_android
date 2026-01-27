package com.example.pace.ui.search_box

import androidx.fragment.app.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.pace.R
import com.example.pace.databinding.FragmentSearchHistoryBinding

class SearchHistoryFragment : Fragment() {
    private var _binding: FragmentSearchHistoryBinding? = null
    private val binding get() = _binding!!

    var onRouteOptionClick: ((isMyLocation: Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSearchHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnMyLocation.setOnClickListener {
            onRouteOptionClick?.invoke(true)
        }

        binding.btnSelectOnMap.setOnClickListener {
            onRouteOptionClick?.invoke(false)
        }
    }

    fun setRouteOptionsVisible(isVisible: Boolean) {
        if (_binding == null) return
        if (isVisible) {
            binding.layoutRouteOptions.visibility = View.VISIBLE
        } else {
            binding.layoutRouteOptions.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 메모리 누수 방지
    }
}