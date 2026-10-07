package com.cursorandroid.app.data.auth

object DemoAccess {
    const val USERNAME = "demo"
    const val PASSWORD = "demo"

    fun accepts(username: String, password: String): Boolean {
        return username.trim().equals(USERNAME, ignoreCase = true) &&
            password.trim() == PASSWORD
    }
}
