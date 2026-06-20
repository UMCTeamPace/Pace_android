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
    private val menuWidthPx by lazy { dpToPx(120) }
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
        binding.btnHomeEdit.setOnClickListener {
            resetHomeSwipe()
            (parentFragment as? RouteFragment)?.startBookmarkSearch(RouteFragment.BookmarkTarget.HOME)
        }
        binding.btnHomeDelete.setOnClickListener {
            resetHomeSwipe()
            searchViewModel.deleteMyPlace("HOME")
        }

        // WORK 메뉴 버튼
        binding.btnWorkEdit.setOnClickListener {
            resetWorkSwipe()
            (parentFragment as? RouteFragment)?.startBookmarkSearch(RouteFragment.BookmarkTarget.WORK)
        }
        binding.btnWorkDelete.setOnClickListener {
            resetWorkSwipe()
            searchViewModel.deleteMyPlace("WORK")
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
        resetHomeSwipe()

        if (place?.placeId?.isNotBlank() == true) {
            binding.ivHomeIcon.setImageResource(R.drawable.ic_home)
            binding.tvHomeAddress.text = place.name
            binding.tvHomeAddress.setTextColor(Color.BLACK)

            binding.layoutHomeForeground.setOnTouchListener(
                SwipeOnTouchListener(menuWidthPx.toFloat()) {
                    (parentFragment as? RouteFragment)?.startBookmarkSearch(RouteFragment.BookmarkTarget.HOME)
                }
            )
            binding.layoutHomeForeground.setOnClickListener(null)

        } else {
            binding.tvHomeAddress.text = "집을 등록하세요"
            binding.ivHomeIcon.setImageResource(R.drawable.ic_home_unselected)
            binding.tvHomeAddress.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_500))

            binding.layoutHomeForeground.setOnTouchListener(null)

            binding.layoutHomeForeground.setOnClickListener {
                (parentFragment as? RouteFragment)?.startBookmarkSearch(RouteFragment.BookmarkTarget.HOME)
            }
        }
    }

    private fun updateWorkUI(place: MyPlace?) {
        if (place?.placeId?.isNotBlank() == true) {
            binding.ivWorkIcon.setImageResource(R.drawable.ic_work_selected)
            binding.tvWorkName.text = place.name
            binding.tvWorkName.setTextColor(Color.BLACK)

            binding.layoutWorkForeground.setOnTouchListener(
                SwipeOnTouchListener(menuWidthPx.toFloat()) {
                    (parentFragment as? RouteFragment)?.startBookmarkSearch(RouteFragment.BookmarkTarget.WORK)
                }
            )
            binding.layoutWorkForeground.setOnClickListener(null)
        } else {
            binding.tvWorkName.text = "학교/회사를 등록하세요"
            binding.ivWorkIcon.setImageResource(R.drawable.ic_work_unselected)
            binding.tvWorkName.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_500))

            binding.layoutWorkForeground.setOnTouchListener(null)

            // 클릭 시 등록
            binding.layoutWorkForeground.setOnClickListener {
                (parentFragment as? RouteFragment)?.startBookmarkSearch(RouteFragment.BookmarkTarget.WORK)
            }
        }
    }

    private fun resetHomeSwipe() {
        binding.layoutHomeForeground.animate().translationX(0f).setDuration(0).start()
    }

    private fun resetWorkSwipe() {
        binding.layoutWorkForeground.animate().translationX(0f).setDuration(0).start()
    }

    private fun dpToPx(dp: Int): Float {
        return dp * resources.displayMetrics.density
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
