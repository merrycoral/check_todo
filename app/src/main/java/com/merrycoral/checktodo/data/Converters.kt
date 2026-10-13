package com.merrycoral.checktodo.data

import androidx.room.TypeConverter

class Converters {
    /** 알림 시각 목록 ↔ "480,1200" 형태의 문자열 */
    @TypeConverter
    fun fromTimes(times: List<Int>): String = times.joinToString(",")

    @TypeConverter
    fun toTimes(value: String): List<Int> = value.split(",").map { it.toInt() }
}
