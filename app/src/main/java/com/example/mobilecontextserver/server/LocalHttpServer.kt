package com.example.mobilecontextserver.server

import android.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * 안드로이드 기기 내부에서 실행되는 간단한 HTTP 서버
 * Node.js 서버에 연결 문제가 있을 때 대체용으로 사용
 */
class LocalHttpServer(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var statusCallback: ((String) -> Unit)? = null
    
    var isRunning = false
        private set
    
    fun setStatusCallback(callback: (String) -> Unit) {
        statusCallback = callback
    }
    
    fun start() {
        if (isRunning) {
            Log.d(TAG, "서버가 이미 실행 중입니다")
            return
        }
        
        serverThread = thread {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                
                Log.d(TAG, "로컬 서버 시작됨 - 포트: $port")
                statusCallback?.invoke("로컬 서버가 포트 $port 에서 실행 중입니다")
                
                // 접속 대기 반복
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        // 클라이언트 요청 처리
                        thread {
                            handleRequest(clientSocket)
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "클라이언트 연결 처리 중 오류", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "서버 시작 중 오류", e)
                statusCallback?.invoke("로컬 서버 시작 실패: ${e.message}")
            } finally {
                stop()
            }
        }
    }
    
    private fun handleRequest(clientSocket: Socket) {
        try {
            val clientAddress = clientSocket.inetAddress.hostAddress
            Log.d(TAG, "클라이언트 연결됨: $clientAddress")
            
            // HTTP 응답 헤더와 본문 준비
            val timestamp = System.currentTimeMillis()
            val jsonResponse = """
                {
                    "message": "로컬 HTTP 서버가 작동 중입니다!",
                    "timestamp": "$timestamp",
                    "clientIP": "$clientAddress"
                }
            """.trimIndent()
            
            // HTTP 응답 작성
            val response = """
                HTTP/1.1 200 OK
                Content-Type: application/json
                Content-Length: ${jsonResponse.length}
                
                $jsonResponse
            """.trimIndent().replace("\n", "\r\n") + "\r\n\r\n"
            
            // 클라이언트에게 응답 전송
            val outputStream = clientSocket.getOutputStream()
            outputStream.write(response.toByteArray())
            outputStream.flush()
            
            statusCallback?.invoke("클라이언트 연결 성공: $clientAddress")
        } catch (e: Exception) {
            Log.e(TAG, "클라이언트 처리 중 오류", e)
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                // 무시
            }
        }
    }
    
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // 무시
        }
        serverSocket = null
        serverThread?.interrupt()
        serverThread = null
        Log.d(TAG, "로컬 서버 중지됨")
        statusCallback?.invoke("로컬 서버가 중지되었습니다")
    }
    
    companion object {
        private const val TAG = "LocalHttpServer"
    }
}