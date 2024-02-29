package io.lionweb

import kotlinx.serialization.Serializable

@Serializable
data class IdRange(val first: Long, val last: Long)