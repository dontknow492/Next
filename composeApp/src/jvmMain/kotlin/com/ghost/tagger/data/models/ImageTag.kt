package com.ghost.tagger.data.models

import com.ghost.tagger.data.enums.TagSource
import kotlinx.serialization.Serializable

@Serializable
data class ImageTag(
    val name: String,
    val confidence: Double= 1.0, // 1.0 = Human added, 0.85 = AI
    val source: TagSource = TagSource.FILE
)

