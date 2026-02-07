package com.example.pace.ui.search_box

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pace.PaceApplication
import com.example.pace.R
import com.example.pace.data.model.MyPlace
import com.example.pace.data.viewmodel.SearchViewModel
import com.example.pace.data.viewmodel.SearchViewModelFactory
import com.example.pace.databinding.FragmentBookmarkHomeWorkBinding
import com.example.pace.ui.main.route.RouteFragment
import kotlinx.coroutines.launch

class BookmarkHomeWorkFragment : Fragment() {
    private var _binding: FragmentBookmarkHomeWorkBinding? = null
    private val binding get() = _binding!!

    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as PaceApplication).searchRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookmarkHomeWorkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeBookmarkData()
    }

    private fun setupListeners() {
        binding.layoutHome.setOnClickListener {
            (parentFragment as? RouteFragment)?.startBookmarkSearch(RouteFragment.BookmarkTarget.HOME)
        }

        binding.layoutWork.setOnClickListener {
            (parentFragment as? RouteFragment)?.startBookmarkSearch(RouteFragment.BookmarkTarget.WORK)
        }
    }

    private fun observeBookmarkData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    searchViewModel.getMyPlace("HOME").collect { myPlace ->
                        updateHomeUI(myPlace)
                    }
                }

                launch {
                    searchViewModel.getMyPlace("WORK").collect { myPlace ->
                        updateWorkUI(myPlace)
                    }
                }
            }
        }
    }

    private fun updateHomeUI(place: MyPlace?) {
        if (place != null) {
            binding.tvHomeAddress.text = place.name
            binding.tvHomeAddress.setTextColor(Color.BLACK)
        } else {
            binding.tvHomeAddress.text = "집을 등록하세요"
            binding.tvHomeAddress.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_500))
        }
    }

    private fun updateWorkUI(place: MyPlace?) {
        if (place != null) {
            binding.tvWorkName.text = place.name
            binding.tvWorkName.setTextColor(Color.BLACK)
        } else {
            binding.tvWorkName.text = "학교/회사를 등록하세요"
            binding.tvWorkName.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_500))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}