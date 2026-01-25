package com.example.pace.ui.main.route

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.pace.databinding.FragmentRouteBinding

class RouteFragment: Fragment() {
    lateinit var binding: FragmentRouteBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRouteBinding.inflate(inflater, container, false)
        return binding.root
    }
}