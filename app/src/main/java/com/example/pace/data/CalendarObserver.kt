package com.example.pace.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun createCalendarObserver(context: Context): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    
    context.contentResolver.registerContentObserver(
        CalendarContract.Events.CONTENT_URI,
        true,
        observer
    )

    awaitClose {
        context.contentResolver.unregisterContentObserver(observer)
    }
}
