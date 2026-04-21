package com.example.melodist.ui.components.images

/**
 * Upscales a YouTube thumbnail URL to a target size.
 * Handles both `w226-h226` and `=w120-h120` and `=s120` formats.
 */
@Deprecated(
    message = "Usa com.example.melodist.utils.upscaleThumbnailUrl",
    replaceWith = ReplaceWith("upscaleThumbnailUrl(url, targetSize)", "com.example.melodist.utils.upscaleThumbnailUrl")
)
fun upscaleThumbnailUrl(url: String?, targetSize: Int): String? {
    return com.example.melodist.utils.upscaleThumbnailUrl(url, targetSize)
}

// resize vive en com.example.melodist.utils.ThumbnailUtils
