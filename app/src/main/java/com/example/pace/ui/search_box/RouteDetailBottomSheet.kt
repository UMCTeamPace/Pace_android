package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.pace.data.model.RouteResponseSample
import com.example.pace.databinding.BottomSheetRouteDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class RouteDetailBottomSheet(private val item: RouteResponseSample) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRouteDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRouteDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}