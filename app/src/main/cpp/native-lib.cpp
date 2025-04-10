#include <jni.h>
#include <string>
#include <cstdlib>
#include <android/log.h>
#include "node.h"

#define LOG_TAG "NodeJS-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" jint JNICALL
Java_com_example_mobilecontextserver_MainActivity_startNodeWithArguments(
        JNIEnv *env,
        jobject /* this */,
        jobjectArray arguments) {

    LOGI("Starting Node.js with arguments");

    //argc
    jsize argument_count = env->GetArrayLength(arguments);
    LOGI("Argument count: %d", argument_count);

    //Compute byte size need for all arguments in contiguous memory.
    int c_arguments_size = 0;
    for (int i = 0; i < argument_count ; i++) {
        jstring jargument = (jstring)env->GetObjectArrayElement(arguments, i);
        const char* argument = env->GetStringUTFChars(jargument, 0);
        LOGI("Argument %d: %s", i, argument);
        c_arguments_size += strlen(argument);
        c_arguments_size++; // for '\0'
        env->ReleaseStringUTFChars(jargument, argument);
    }

    LOGI("Calculated arguments size: %d bytes", c_arguments_size);

    //Stores arguments in contiguous memory.
    char* args_buffer = (char*) calloc(c_arguments_size, sizeof(char));
    if (args_buffer == NULL) {
        LOGE("Failed to allocate memory for arguments buffer");
        return -1;
    }
    LOGI("Allocated arguments buffer");

    //argv to pass into node.
    char* argv[argument_count];

    //To iterate through the expected start position of each argument in args_buffer.
    char* current_args_position = args_buffer;

    //Populate the args_buffer and argv.
    for (int i = 0; i < argument_count ; i++)
    {
        jstring jargument = (jstring)env->GetObjectArrayElement(arguments, i);
        const char* current_argument = env->GetStringUTFChars(jargument, 0);

        //Copy current argument to its expected position in args_buffer
        strncpy(current_args_position, current_argument, strlen(current_argument));

        //Save current argument start position in argv
        argv[i] = current_args_position;
        LOGI("Set argv[%d] = %s", i, argv[i]);

        //Increment to the next argument's expected position.
        current_args_position += strlen(current_args_position) + 1;

        env->ReleaseStringUTFChars(jargument, current_argument);
    }

    LOGI("About to start Node.js runtime");
    
    //Start node, with argc and argv.
    LOGI("Before calling node::Start");
    int node_result = node::Start(argument_count, argv);
    LOGI("After calling node::Start, result: %d", node_result); // 이 로그가 뜨는지 확인!
    free(args_buffer);
    LOGI("Freed arguments buffer");

    return jint(node_result);
}

const char* ToCString(const v8::String::Utf8Value& value) {
    return *value ? *value : "<string conversion failed>";
}

static void LogCallback(const v8::FunctionCallbackInfo<v8::Value>& args) {
    if (args.Length() < 1) return;
    
    v8::HandleScope scope(args.GetIsolate());
    v8::String::Utf8Value str(args.GetIsolate(), args[0]);
    const char* cstr = ToCString(str);
    
    __android_log_print(ANDROID_LOG_INFO, "NodeJS-Console", "%s", cstr);
}

// Node.js 시작하기 전에 이 함수를 호출하여 콘솔 로깅을 설정
void SetupConsoleLog(v8::Isolate* isolate, v8::Local<v8::Context> context) {
    v8::Local<v8::Object> global = context->Global();
    v8::Local<v8::Object> console = v8::Object::New(isolate);
    
    console->Set(
        context,
        v8::String::NewFromUtf8(isolate, "log").ToLocalChecked(),
        v8::FunctionTemplate::New(isolate, LogCallback)->GetFunction(context).ToLocalChecked()
    ).Check();
    
    global->Set(
        context,
        v8::String::NewFromUtf8(isolate, "console").ToLocalChecked(),
        console
    ).Check();
}
