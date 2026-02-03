package com.ghost.tagger.ui.actions

import com.ghost.tagger.data.models.ImageTag

sealed interface DetailAction {
    data class UpdateDescription(val value: String) : DetailAction
    data class RemoveTag(val tag: ImageTag) : DetailAction
    data class AddTag(val tag: String) : DetailAction
    object ClearTags : DetailAction
    object GenerateTags : DetailAction
    object SaveMetadata : DetailAction
    object OpenInExplorer : DetailAction
    object ClosePreview : DetailAction
}