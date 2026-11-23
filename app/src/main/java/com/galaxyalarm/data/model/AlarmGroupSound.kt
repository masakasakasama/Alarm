package com.galaxyalarm.data.model

internal const val SILENT_GROUP_NAME = "音無し"

internal fun isSilentGroup(name: String?): Boolean = when (name?.trim()) {
    SILENT_GROUP_NAME, "音なし", "音無" -> true
    else -> false
}
