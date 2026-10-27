# 1. Полностью перезаписываем build.gradle правильной конфигурацией
$gradleCode = @'
plugins {
    id 'com.android.application'
}

android {
    namespace 'dev.shadealpha'
    compileSdk 35

    defaultConfig {
        applicationId "dev.shadealpha"
        minSdk 31
        targetSdk 35
        versionCode 3
        versionName "1.0.1"
    }

    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly project(':xposed-stub')
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.12.0'
}
'@
[IO.File]::WriteAllText('app\build.gradle', $gradleCode, [Text.UTF8Encoding]::new($false))

# 2. На всякий случай дублируем правила ProGuard
$proguardCode = @'
-keep class de.robv.android.xposed.** { *; }
-keep class dev.shadealpha.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }
-dontwarn androidx.appcompat.**
-dontwarn com.google.android.material.**
'@
[IO.File]::WriteAllText('app\proguard-rules.pro', $proguardCode, [Text.UTF8Encoding]::new($false))

Write-Host "✅ Build.gradle исправлен, сжатие APK включено!"