package com.galaxyalarm.data.db

import androidx.room.TypeConverter
import com.galaxyalarm.data.model.EventResult
import com.galaxyalarm.data.model.OccurrenceStatus
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern

class Converters {
    @TypeConverter fun soundMode(v: SoundMode) = v.name
    @TypeConverter fun toSoundMode(v: String) = SoundMode.valueOf(v)

    @TypeConverter fun status(v: OccurrenceStatus) = v.name
    @TypeConverter fun toStatus(v: String) = OccurrenceStatus.valueOf(v)

    @TypeConverter fun result(v: EventResult) = v.name
    @TypeConverter fun toResult(v: String) = EventResult.valueOf(v)

    @TypeConverter fun vibration(v: VibrationPattern) = v.name
    @TypeConverter fun toVibration(v: String) = VibrationPattern.valueOf(v)
}
