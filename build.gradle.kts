// 根构建脚本：仅声明插件版本，由 app 模块应用。
// 技术栈固定：AGP 8.2.x / Kotlin 1.9.22 / Hilt 2.48
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
}
