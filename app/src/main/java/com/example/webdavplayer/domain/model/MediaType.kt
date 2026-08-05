package com.example.webdavplayer.domain.model

import com.example.webdavplayer.domain.common.MediaConstants

/**
 * 媒体类型：视频 / 图片 / 其他。
 * 识别规则（§8）：contentType 优先，扩展名兜底。
 *
 * 注：音频文件不再作为独立类型支持（产品方向聚焦视频+图片），
 * 原音频文件按 contentType/扩展名识别失败后归为 OTHER。
 */
enum class MediaType {
    VIDEO,
    IMAGE,
    OTHER,
}

/** 媒体类型识别工具（contentType 优先，扩展名兜底）。 */
object MediaTypeClassifier {
    /** 仅由 contentType 推断（以 video/ 或 image/ 开头）。 */
    fun fromContentType(contentType: String?): MediaType? {
        if (contentType.isNullOrBlank()) return null
        return when {
            contentType.startsWith("video/", ignoreCase = true) -> MediaType.VIDEO
            contentType.startsWith("image/", ignoreCase = true) -> MediaType.IMAGE
            else -> null
        }
    }

    /** 仅由文件名扩展名推断。 */
    fun fromExtension(name: String): MediaType? {
        val ext = MediaConstants.extensionOf(name)
        return when {
            ext in MediaConstants.VIDEO_EXTENSIONS -> MediaType.VIDEO
            ext in MediaConstants.IMAGE_EXTENSIONS -> MediaType.IMAGE
            else -> null
        }
    }

    /** 综合识别：contentType 优先，扩展名兜底，都不命中为 OTHER。 */
    fun classify(contentType: String?, name: String): MediaType =
        fromContentType(contentType) ?: fromExtension(name) ?: MediaType.OTHER
}
