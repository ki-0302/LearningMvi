package com.maho_ya.learningmvi

class UserApi {

    fun getUser(): List<User> = listOf(
        User("test1"),
        User("test2"),
        User("test3"),
    )
}