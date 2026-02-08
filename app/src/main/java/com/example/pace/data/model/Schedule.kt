package com.example.pace.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 캘린더 이벤트 정보를 담는 데이터 클래스.
 * Calendar Provider와 앱 자체 데이터를 모두 표현할 수 있도록 설계되었습니다.
 * Room 데이터베이스의 'schedules' 테이블과 매핑됩니다.
 *
 * @param id 이벤트의 고유 ID (Calendar Provider의 Events._ID). 이 테이블의 Primary Key.
 * @param title 일정 제목 (Events.TITLE)
 * @param startDate 일정 시작 날짜 (YYYY-MM-DD 형식)
 * @param endDate 일정 종료 날짜 (YYYY-MM-DD 형식)
 * @param startTime 일정 시작 시간 (HH:mm 형식)
 * @param endTime 일정 종료 시간 (HH:mm 형식)
 * @param isAllDay 하루 종일 일정 여부 (Events.ALL_DAY)
 * @param memo 일정 설명 또는 메모 (Events.DESCRIPTION)
 * @param location 장소 (Events.EVENT_LOCATION)
 * @param repeatRule 반복 규칙 (Events.RRULE)
 * @param calendarId 이 이벤트가 속한 캘린더의 ID (Events.CALENDAR_ID)
 * @param calendarDisplayName 이 이벤트가 속한 캘린더의 이름 (Events.CALENDAR_DISPLAY_NAME)
 * @param calendarAccountName 이 이벤트가 속한 캘린더의 계정 (Calendars.ACCOUNT_NAME)
 * @param reminders 알림 시간 목록 (분 단위, 예: [10, 30] -> 10분 전, 30분 전)
 * @param withRoute Pace 앱의 경로 포함 여부 (앱 고유 데이터)
 * @param isCompleted Pace 앱의 일정 완료 여부 (앱 고유 데이터)
 * @param isPinned Pace 앱의 일정 고정 여부 (앱 고유 데이터)
 * @param type 일정의 종류 (e.g., "NORMAL", "ROUTE") (앱 고유 데이터)
 */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "start_date")
    val startDate: String,

    @ColumnInfo(name = "end_date")
    val endDate: String,

    @ColumnInfo(name = "start_time")
    val startTime: String,

    @ColumnInfo(name = "end_time")
    val endTime: String,

    @ColumnInfo(name = "is_all_day")
    val isAllDay: Boolean = false,

    @ColumnInfo(name = "memo")
    val memo: String?,

    @ColumnInfo(name = "location")
    val location: String?,

    @ColumnInfo(name = "repeat_rule")
    val repeatRule: String?,

    @ColumnInfo(name = "exdate")
    val exdate: String? = null,

    @ColumnInfo(name = "calendar_id")
    val calendarId: Long,

    @ColumnInfo(name = "calendar_display_name")
    val calendarDisplayName: String?,

    @ColumnInfo(name = "calendar_account_name")
    val calendarAccountName: String?,

    @ColumnInfo(name = "reminders")
    val reminders: List<Int> = emptyList(),

    @ColumnInfo(name = "with_route")
    val withRoute: Boolean = false,

    @ColumnInfo(name = "is_completed")
    var isCompleted: Boolean = false,
    
    @ColumnInfo(name = "is_pinned")
    var isPinned: Boolean = false,

    @ColumnInfo(name = "is_swiped", defaultValue = "0")
    var isSwiped: Boolean = false,

    @ColumnInfo(name = "type", defaultValue = "NORMAL")
    val type: String = "NORMAL",

    @ColumnInfo(name = "event_color")
    val eventColor: Int?,

    @ColumnInfo(name = "calendar_color")
    val calendarColor: Int?
)
