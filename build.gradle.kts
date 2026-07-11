// 根构建脚本：仅声明插件版本，由 app 模块应用。
// 技术栈固定：AGP 8.2.x / Kotlin 1.9.22 / Hilt 2.51.1
// 注：Hilt 2.51.1 + KSP 在此环境仍报 error.NonExistentClass，故回退到成熟的 kapt 路径。
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.24" apply false
}
