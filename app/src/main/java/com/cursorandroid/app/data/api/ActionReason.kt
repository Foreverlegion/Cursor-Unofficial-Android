package com.cursorandroid.app.data.api

fun Throwable.actionReason(): String {
    val text = when (this) {
        is ApiException -> displayMessage()
        else -> message.orEmpty()
    }.trim()
    return text.ifBlank { "Request failed" }.lineSequence().first().take(160)
}

/** Archive is already done on the server, so a second archive is not a failure. */
fun Throwable.alreadyArchived(): Boolean {
    val api = this as? ApiException ?: return false
    return api.isArchivedAgent
}
