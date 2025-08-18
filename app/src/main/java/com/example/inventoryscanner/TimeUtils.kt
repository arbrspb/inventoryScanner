package com.example.inventoryscanner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val statusTimeFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

fun formatStatusTime(ts: Long?): String =
    if (ts == null || ts == 0L) "—" else statusTimeFormat.format(Date(ts))