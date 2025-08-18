package com.example.inventoryscanner

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

suspend fun loadKitTemplate(context: Context): List<KitTemplateEntry> = withContext(Dispatchers.IO) {
    val input = context.assets.open("kit_template.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(input)
    val result = mutableListOf<KitTemplateEntry>()
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        result.add(
            KitTemplateEntry(
                code = obj.getString("code"),
                name = if (obj.has("name")) obj.optString("name", null) else null
            )
        )
    }
    result
}