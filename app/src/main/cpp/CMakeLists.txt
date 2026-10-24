cmake_minimum_required(VERSION 3.22.1)
project("andtllm")

# Try to locate llama.cpp
set(LLAMA_DIR "${CMAKE_SOURCE_DIR}/../../../native/llama.cpp")
if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    set(LLAMA_DIR "${CMAKE_SOURCE_DIR}/../../../../native/llama.cpp")
endif()

if(EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(STATUS "Found llama.cpp at ${LLAMA_DIR}")
    set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    add_subdirectory(${LLAMA_DIR} llama_build)
else()
    message(WARNING "llama.cpp not found. Add as submodule at native/llama.cpp. Building JNI stub only.")
endif()

add_library(andtllm SHARED
    llama_jni.cpp
)

target_include_directories(andtllm PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/ggml/include
)

if(TARGET llama)
    target_link_libraries(andtllm PRIVATE llama)
else()
    message(WARNING "Target 'llama' not found; implement stubs in llama_jni.cpp")
endif()

find_library(log-lib log)
target_link_libraries(andtllm PRIVATE ${log-lib})
