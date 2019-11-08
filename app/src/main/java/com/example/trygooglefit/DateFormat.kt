package com.example.trygooglefit

import java.text.SimpleDateFormat
import java.util.*

fun Date.formatString(): String {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(this)
}