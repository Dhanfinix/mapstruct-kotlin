package com.github.pozo.domain

import com.github.pozo.KotlinBuilder

interface Bar

@KotlinBuilder
data class Foo<T : Bar>(
    val bar: T,
    val kar: String
) {

    fun bar(k: String): T {
        return bar
    }
}

@KotlinBuilder
data class Light(
    val bar: Bar,
    val lol: String
)