package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val label: String = "Alarm",
    val monday: Boolean = true,
    val tuesday: Boolean = true,
    val wednesday: Boolean = true,
    val thursday: Boolean = true,
    val friday: Boolean = true,
    val saturday: Boolean = false,
    val sunday: Boolean = false,
    val shakesRequired: Int = 20
) {
    val formattedTime: String
        get() {
            val h = if (hour == 0 || hour == 12) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            return String.format("%02d:%02d %s", h, minute, amPm)
        }

    val activeDaysString: String
        get() {
            val list = mutableListOf<String>()
            if (monday) list.add("Mon")
            if (tuesday) list.add("Tue")
            if (wednesday) list.add("Wed")
            if (thursday) list.add("Thu")
            if (friday) list.add("Fri")
            if (saturday) list.add("Sat")
            if (sunday) list.add("Sun")
            return if (list.size == 7) "Every day"
            else if (list.size == 5 && !saturday && !sunday) "Weekdays"
            else if (list.size == 2 && saturday && sunday) "Weekends"
            else if (list.isEmpty()) "Once off"
            else list.joinToString(", ")
        }
}
