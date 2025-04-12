plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
}

android {
    namespace = "com.example.mobilecontextserver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mobilecontextserver"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

        // CMake 설정을 defaultConfig 내부에 유지
        externalNativeBuild {
            cmake {
                cppFlags("")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs") // Node.js 라이브러리 위치
            assets.srcDirs("src/main/assets")
        }
    }

    // 안정적인 방식으로 externalNativeBuild 설정
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")

        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.10"
        buildPython("/opt/homebrew/bin/python3.10") // Homebrew 설치 경로에 따라 다를 수 있음

        pip {
            // 모든 패키지에 적용되는 글로벌 옵션
            options("--no-deps")

            // 필요한 패키지들 직접 명시
            install("requests")
            install("starlette>=0.27")
            install("httpx-sse>=0.4")
            install("sse-starlette>=1.6.1")
            install("uvicorn>=0.23.1")
            install("anyio>=4.5")
            install("httpx>=0.27")
            install("mcp")
        }
    }
}

// 종속성 충돌 해결을 위한 설정
configurations.all {
    resolutionStrategy {
        // 오류를 발생시키는 HTTP 클라이언트 라이브러리 강제 버전 지정
        force("org.apache.httpcomponents.client5:httpclient5:5.3.1")
        force("org.apache.httpcomponents.core5:httpcore5:5.2.4")
        force("org.apache.httpcomponents.core5:httpcore5-h2:5.2.4")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.appcompat)
    implementation(libs.slf4j.nop)

    // MCP 관련 의존성
    implementation("io.modelcontextprotocol:kotlin-sdk:0.4.0")
    implementation(libs.anthropic.java)

    // 비동기 처리 관련 라이브러리
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.ktx.v270)

    // 채팅 UI 라이브러리
    implementation(libs.androidx.runtime.livedata)
    
    // HTTP 클라이언트 의존성 (OkHttp 또는 Ktor 사용)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    // Ktor 클라이언트 의존성 추가
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.plugins)
    implementation(libs.jackson.module.kotlin)

    // HTTP 관련 라이브러리 - 충돌을 유발하는 패키지는 runtimeOnly로 변경
    runtimeOnly(libs.httpclient5)
    runtimeOnly(libs.httpcore5)
    runtimeOnly(libs.httpcore5.h2)

    // Jackson Kotlin 모듈 추가
    implementation(libs.jackson.module.kotlin)

    // Jackson Java 8 데이터 타입 모듈 추가
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.jackson.datatype.jsr310)
}

// 서버 디렉토리의 패키지 설치를 위한 태스크 (필요시 수동 실행)
tasks.register("installServerModules") {
    doLast {
        // 모든 서버 디렉토리 찾기
        val serversDir = file("src/main/assets/servers")
        serversDir.listFiles()?.filter { it.isDirectory }?.forEach { serverDir ->
            // 각 서버 디렉토리에서 npm install 실행
            if (file("${serverDir.absolutePath}/package.json").exists()) {
                println("Installing node modules for ${serverDir.name}...")
                project.exec {
                    workingDir = serverDir
                    commandLine("npm", "install", "--production")
                }
            }
        }
    }
}

// 필요한 디렉토리와 파일 생성 태스크
tasks.register("createNativeDirectories") {
    doLast {
        // 디렉토리 생성
        mkdir("src/main/jniLibs")
        mkdir("src/main/jniLibs/arm64-v8a")
        mkdir("src/main/jniLibs/armeabi-v7a")
        mkdir("src/main/jniLibs/x86_64")
        mkdir("src/main/cpp")
        mkdir("src/main/assets/servers")

        // CMakeLists.txt 생성
        val cmakeFile = file("src/main/cpp/CMakeLists.txt")
        if (!cmakeFile.exists()) {
            cmakeFile.writeText("""
                cmake_minimum_required(VERSION 3.22.1)
                project("mobilecontextserver")
                
                # native-lib 라이브러리 생성
                add_library(
                    native-lib
                    SHARED
                    native-lib.cpp
                )
                
                find_library(log-lib log)
                target_link_libraries(
                    native-lib
                    ${"\${log-lib}"}
                )
            """.trimIndent())
        }

        // native-lib.cpp 생성
        val nativeLibFile = file("src/main/cpp/native-lib.cpp")
        if (!nativeLibFile.exists()) {
            nativeLibFile.writeText("""
                #include <jni.h>
                #include <string>
                
                extern "C" JNIEXPORT jint JNICALL
                Java_com_example_mobilecontextserver_MainActivity_startNodeWithArguments(
                        JNIEnv *env,
                        jobject /* this */,
                        jobjectArray arguments) {
                    // 임시 구현 - 나중에 node.h가 있을 때 완성
                    return 0;
                }
            """.trimIndent())
        }
    }
}

// 빌드 전에 디렉토리 생성 태스크 실행
tasks.named("preBuild") {
    dependsOn("createNativeDirectories")
}