package com.example.webdavplayer.ui.common

import com.example.webdavplayer.domain.common.MediaConstants

/**
 * 视频扩展名白名单（集中常量，详见 [MediaConstants.VIDEO_EXTENSIONS]）。
 * 统一的共享约定来源（§8），避免散落。
 */
val VIDEO_EXTENSIONS: Set<String> get() = MediaConstants.VIDEO_EXTENSIONS
