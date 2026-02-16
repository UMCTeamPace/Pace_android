package com.example.pace.data.repository

import com.example.pace.data.db.MyPlaceDao
import com.example.pace.data.db.RecentRouteDao
import com.example.pace.data.db.SearchDao
import com.example.pace.data.model.MyPlace
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.model.RecentPlace
import com.example.pace.data.model.RecentRoute
import com.example.pace.data.model.RecentSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SearchRepository(
    private val searchDao: SearchDao,
    private val recentRouteDao: RecentRouteDao,
    private val myPlaceDao: MyPlaceDao
) {
    val recentSearches: Flow<List<RecentSearch>> = searchDao.getRecentSearches()
    val recentPlaces: Flow<List<RecentPlace>> = searchDao.getRecentPlaces()

    val recentRoutes: Flow<List<RecentRoute>> = recentRouteDao.getRecentRoutes()

    val allHistory: Flow<List<RecentHistoryItem>> = combine(
        recentSearches,
        recentPlaces
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

    suspend fun insertSearch(query: String) {
        searchDao.insertSearch(RecentSearch(query = query))
    }

    suspend fun deleteSearch(search: RecentSearch) {
        searchDao.deleteSearch(search)
    }

    suspend fun deleteSearchByQuery(query: String) {
        searchDao.deleteSearchByQuery(query)
    }

    suspend fun insertPlace(place: RecentPlace) {
        searchDao.insertPlace(place)
    }

    suspend fun deletePlace(place: RecentPlace) {
        searchDao.deletePlace(place)
    }

    // 최근 경로
    suspend fun insertRecentRoute(route: RecentRoute) {
        recentRouteDao.insertRecentRoute(route)
    }

    // 최근 경로 삭제
    suspend fun deleteRecentRoute(route: RecentRoute) {
        recentRouteDao.deleteRecentRoute(route)
    }

    // 집, 회사
    suspend fun insertMyPlace(myPlace: MyPlace) {
        myPlaceDao.insertMyPlace(myPlace)
    }

    suspend fun deleteMyPlace(type: String) {
        searchDao.deleteMyPlaceByType(type)
    }

    fun getMyPlaceByType(type: String) = myPlaceDao.getMyPlaceByType(type)

    // 30일 넘으면 삭제
    suspend fun deleteExpiredData() {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        val threshold = System.currentTimeMillis() - thirtyDaysInMillis

        searchDao.deleteOldPlaces(threshold)
        searchDao.deleteOldSearches(threshold)
        recentRouteDao.deleteOldRoutes(threshold)
    }
}