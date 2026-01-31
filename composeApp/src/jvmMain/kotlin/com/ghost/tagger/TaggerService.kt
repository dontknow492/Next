package com.ghost.tagger

// 1. Define the Tagger as an 'object' so it is only created once
object TaggerService {
    private const val BASE_PATH =
        """C:\Users\Ghost\.cache\huggingface\hub\models--SmilingWolf--wd-eva02-large-tagger-v3\snapshots\b25b82a03f7282e41aa2f257a52c7583b710bd1c\"""

    // 'lazy' means it won't load until the very moment you first use it
    val instance: ImageTagger by lazy {
        ImageTagger(
            modelPath = "${BASE_PATH}model.onnx",
            csvPath = "${BASE_PATH}selected_tags.csv"
        )
    }
}