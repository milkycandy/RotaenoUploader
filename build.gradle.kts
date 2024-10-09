// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        //增加Android Gradle插件版本号配置，{version}为实际的Gradle插件版本号，例如7.3.1。
        classpath ("com.android.tools.build:gradle:8.5.0")
        //增加AGC插件配置，请您参见AGC插件依赖关系选择合适的AGC插件版本。
//        classpath ("com.huawei.agconnect:agcp:1.9.1.301")
    }
}

plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}