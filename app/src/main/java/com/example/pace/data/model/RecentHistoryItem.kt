package com.example.pace.data.model

data class RecentHistoryItem(
    val type: Int,
    val mainText: String,
    val timestamp: Long,

    val placeEntity: RecentPlace? = null,

    val searchEntity: RecentSearch? = null
) {
    companion object {
        const val TYPE_SEARCH_TEXT = 0
        const val TYPE_PLACE = 1
    }
}
