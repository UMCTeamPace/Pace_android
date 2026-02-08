package com.example.pace.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.pace.data.model.MyPlace
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.model.RecentPlace
import com.example.pace.data.model.RecentRoute
import com.example.pace.data.repository.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: SearchRepository) : ViewModel() {
    val allHistory: StateFlow<List<RecentHistoryItem>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentRoutes: StateFlow<List<RecentRoute>> = repository.recentRoutes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    fun insertSearch(query: String) = viewModelScope.launch {
        repository.insertSearch(query)
    }

    fun insertPlace(place: RecentPlace) = viewModelScope.launch {
        repository.insertPlace(place)
    }

    fun insertRecentRoute(route: RecentRoute) = viewModelScope.launch {
        repository.insertRecentRoute(route)
    }

    fun deleteHistoryItem(item: RecentHistoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.type == RecentHistoryItem.TYPE_SEARCH_TEXT) {
                repository.deleteSearchByQuery(item.mainText)
            } else if (item.type == RecentHistoryItem.TYPE_PLACE) {
                item.placeEntity?.let { place ->
                    repository.deletePlace(place)
                }
            }
        }
    }

    fun deleteRecentRoute(route: RecentRoute) = viewModelScope.launch {
        repository.deleteRecentRoute(route)
    }

    fun insertMyPlace(myPlace: MyPlace) = viewModelScope.launch {
        repository.insertMyPlace(myPlace)
    }

    fun getMyPlace(type: String) = repository.getMyPlaceByType(type)

    fun deleteExpiredData() = viewModelScope.launch {
        repository.deleteExpiredData()
    }
}

class SearchViewModelFactory(private val repository: SearchRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}