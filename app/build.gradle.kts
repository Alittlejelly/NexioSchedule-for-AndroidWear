import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.haooz.chedule"
    // 与已验证可在小米手表5安装的工程一致：不用 37。
    // 37 的 AAPT2 产物会被部分推送工具的老解析器判成 null 字段
    compileSdk = 36

    defaultConfig {
        // 与手机端（com.haooz.chedule）区分开：同包名会让手表推送器在覆盖安装时
        // 走「已存在应用」的签名/来源校验而失败。Wear 独立包加 .wear 后缀也是常规做法。
        applicationId = "com.haooz.chedule.wear"
        // Wear OS 3.0 起（API 30）；低于该版本没有 Compose for Wear OS 运行环境
        minSdk = 30
        // 与已验证可在小米手表 5 上安装的工程保持一致：targetSdk 不用 37
        targetSdk = 36
        versionCode = 102
        versionName = "1.0.2 Wear"

        // 只保留手表实际使用的 ABI（与已验证可推送的工程一致）：
        // 去掉 x86/x86_64 可减小体积、缩短蓝牙推送耗时，也避免原生库匹配出岔子
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        getByName("debug") {
            // minSdk 30 时 AGP 默认只签 v2。部分手表定制系统的安装器只认 JAR(v1) 签名，
            // 纯 v2 会被判为「包无效」。显式打开 v1 以兼容侧载。
            enableV1Signing = true
            enableV2Signing = true
        }
        create("release") {
            // 用独立 release 证书签，而不是众所周知的 debug key：
            // 部分厂商安装校验会拉黑 CN=Android Debug 的调试证书。
            // 凭据放 local.properties（不入库），缺失时退回 debug keystore 以便本地能构建。
            val props = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            val storePath = props.getProperty("nexio.store.file")
            // 路径可能相对仓库根（app/nexio-watch.jks）也可能相对 app 模块，
            // 两处都试：之前只用 file()（相对 app/）解析 "app/nexio-watch.jks"
            // 变成 app/app/nexio-watch.jks 找不到文件，静默退回 debug 证书
            val store = storePath?.let {
                sequenceOf(rootProject.file(it), file(it)).firstOrNull { f -> f.exists() }
            }
            if (store != null) {
                storeFile = store
                storePassword = props.getProperty("nexio.store.password", "")
                keyAlias = props.getProperty("nexio.key.alias", "nexio")
                keyPassword = props.getProperty("nexio.key.password", "")
            } else {
                val home = System.getProperty("user.home")
                storeFile = file("$home/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
            // 与 debug 一致：部分手表定制系统的安装器只认 v1(JAR) 签名，纯 v2 会被判「包无效」
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            // R8 混淆 + 资源裁剪：APK 体积几乎全在 dex（未混淆时 22MB+），主要收益在这。
            // 全工程无反射调用（已核查），Compose/Wear 库自带 consumer rules，可放心开启。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    androidResources {
        // 只保留中英文语言资源，裁掉 Wear/Compose 库带来的几十种 locale（resources.arsc 瘦身）
        localeFilters += listOf("zh", "en")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Robolectric 需要读取 res / manifest，才能真实渲染界面
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // ===== Compose 基础 =====
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ===== Wear Compose（列表 / 圆屏 / M3 组件）=====
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.androidx.wear)

    // ===== 数据同步 =====
    // 不走 Wear OS Data Layer：国行 HyperOS 手表没有 GMS / 配对体系，节点恒为空。
    // 传输层改为局域网（UDP 发现 + TCP 拉取），见 sync/LanSync.kt。

    debugImplementation(libs.androidx.compose.ui.tooling)

    // ===== 离屏截图验证（JVM，无需设备）=====
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
