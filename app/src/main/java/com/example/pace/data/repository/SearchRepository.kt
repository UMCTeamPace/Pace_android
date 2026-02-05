package com.example.pace.data.repository

import com.example.pace.data.db.RecentRouteDao
import com.example.pace.data.db.SearchDao
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.model.RecentPlace
import com.example.pace.data.model.RecentSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SearchRepository(
    private val searchDao: SearchDao,
    private val recentRouteDao: RecentRouteDao
) {
    val recentSearches: Flow<List<RecentSearch>> = searchDao.getRecentSearches()

    suspend fun insertSearch(query: String) {
        searchDao.insertSearch(RecentSearch(query = query))
    }

    suspend fun deleteSearch(search: RecentSearch) {
        searchDao.deleteSearch(search)
    }

    // 장소 관련 함수들
    val recentPlaces: Flow<List<RecentPlace>> = searchDao.getRecentPlaces()

    suspend fun insertPlace(place: RecentPlace) {
        searchDao.insertPlace(place)
    }

    suspend fun deletePlace(place: RecentPlace) {
        searchDao.deletePlace(place)
    }

    val allHistory: Flow<List<RecentHistoryItem>> = combine(
        searchDao.getRecentSearches(),
        searchDao.getRecentPlaces()
    ) { searches, places ->
        val historyList = mutableListOf<RecentHistoryItem>()

        historyList.addAll(searches.map {
            RecentHistoryItem(RecentHistoryItem.TYPE_SEARCH_TEXT, it.query, it.timestamp, searchEntity = it)
        })

        historyList.addAll(places.map {
            RecentHistoryItem(RecentHistoryItem.TYPE_PLACE, it.name, it.timestamp, placeEntity = it)
        })

        historyList.sortByDescending { it.timestamp }
        historyList
    }

    // 30일 넘으면 삭제
    suspend fun deleteExpiredData() {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        val threshold = System.currentTimeMillis() - thirtyDaysInMillis

        searchDao.deleteOldPlaces(threshold)
        searchDao.deleteOldSearches(threshold)
        recentRouteDao.deleteOldRoutes(threshold)
    }
}