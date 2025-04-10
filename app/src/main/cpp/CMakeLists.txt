cmake_minimum_required(VERSION 3.22.1)
project(mobilecontextserver)

# Node.js 라이브러리 경로 설정
set(NODE_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs)
set(NODE_INCLUDE_DIR ${NODE_ROOT}/node/include)

# Node.js 헤더 파일 경로 설정
include_directories(${NODE_INCLUDE_DIR})

# native-lib 프로젝트 추가
add_library(native-lib SHARED
           native-lib.cpp)

# ANDROID_ABI는 CMake에서 자동으로 정의되는 변수입니다
message(STATUS "Building for ABI: ${ANDROID_ABI}")

# libnode.so 직접 경로 지정 (ANDROID_ABI 사용)
set(NODE_LIB_PATH ${NODE_ROOT}/${ANDROID_ABI}/libnode.so)
message(STATUS "Looking for Node.js library at: ${NODE_LIB_PATH}")

# node 라이브러리를 가져오기
add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES IMPORTED_LOCATION ${NODE_LIB_PATH})

# 링크 라이브러리 지정
find_library(LOG_LIB log)

target_link_libraries(native-lib
                     node  # 가져온 node 라이브러리 사용
                     android
                     ${LOG_LIB})

target_compile_options(native-lib PRIVATE
    -Wall
    -Wextra
    -fexceptions
    -frtti
)