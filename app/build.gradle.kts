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

    // Jackson Kotlin 모듈 추가
    implementation(libs.jackson.module.kotlin)

    // Jackson Java 8 데이터 타입 모듈 추가
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.jackson.datatype.jsr310)
}

// Node 패키지 추가하는 태스크
tasks.register("installNodeModules") {
    doLast {
        project.exec {
            workingDir = file("src/main/assets/nodejs-project")
            commandLine("npm", "install", "--production")
        }

        project.exec {
            workingDir = file("src/main/assets/nodejs-project")
            commandLine("npx", "esbuild", "--bundle", "index.js", "--outfile=bundle.js", "--platform=node")
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

        // nodejs-project 디렉토리 및 main.js 생성
        mkdir("src/main/assets/nodejs-project")
        val mainJsFile = file("src/main/assets/nodejs-project/main.js")
        if (!mainJsFile.exists()) {
            mainJsFile.writeText("""
                // 간단한 HTTP 서버
                const http = require('http');
                
                const server = http.createServer((req, res) => {
                  res.writeHead(200, {'Content-Type': 'application/json'});
                  res.end(JSON.stringify({
                    message: 'Node.js 서버가 작동 중입니다!',
                    timestamp: new Date().toISOString()
                  }));
                });
                
                server.listen(3000, '127.0.0.1', () => {
                  console.log('서버가 http://127.0.0.1:3000 에서 실행 중입니다');
                });
            """.trimIndent())
        }
    }
}

// 빌드 전에 디렉토리 생성 태스크 실행
tasks.named("preBuild") {
    dependsOn("createNativeDirectories")
}