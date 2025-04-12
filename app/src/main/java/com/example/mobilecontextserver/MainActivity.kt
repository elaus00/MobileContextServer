package com.example.mobilecontextserver

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mobilecontextserver.server.LocalHttpServer
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

const val NODE_LOG_FILE = "/data/data/com.example.mobilecontextserver/files/node_log.log"


class MainActivity : ComponentActivity() {
    external fun startNodeWithArguments(arguments: Array<String>): Int
    private val _responseText = mutableStateOf("")
    val responseText = _responseText
    private var localServer: LocalHttpServer? = null
    private var localServerStarted = false

    companion object {
        init {
            try {
                System.loadLibrary("native-lib")
                System.loadLibrary("node")
                Log.d("NodeJS", "네이티브 라이브러리 로드 성공")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("NodeJS", "네이티브 라이브러리 로드 실패", e)
            }
        }
        var nodeStarted = false
    }

    // 사용 가능한 서버 목록 (assets/servers 디렉토리에서 자동으로 로드됨)
    private var availableServers = mutableListOf<String>()
    private var selectedServer = mutableStateOf<String?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 사용 가능한 서버 목록 로드
        loadAvailableServers()
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (selectedServer.value == null) {
                        // 서버 선택 화면 표시
                        ServerSelectionScreen(
                            availableServers = availableServers,
                            onServerSelected = { serverName ->
                                selectedServer.value = serverName
                            }
                        )
                    } else {
                        // 선택된 서버의 제어 화면 표시
                        ServerScreen(
                            serverName = selectedServer.value!!,
                            onStartNode = {
                                if (!nodeStarted) {
                                    startNodeJS(selectedServer.value!!)
                                    nodeStarted = true // 시작 시도 시 일단 true로 설정 (결과에 따라 변경됨)
                                }
                            },
                            onStartLocalServer = {
                                if (!localServerStarted) {
                                    startLocalServer()
                                    // localServerStarted = true // LocalHttpServer 내부 콜백에서 설정되도록 변경 가능
                                }
                            },
                            onStopLocalServer = {
                                stopLocalServer()
                                localServerStarted = false
                            },
                            onTestServer = { testNodeServer() },
                            onChangeServer = {
                                if (!nodeStarted && !localServerStarted) {
                                    selectedServer.value = null
                                } else {
                                    _responseText.value = "서버를 중지한 후 다른 서버를 선택해주세요."
                                }
                            },
                            isNodeStarted = nodeStarted,
                            isLocalServerStarted = localServerStarted
                        )
                    }
                }
            }
        }
    }

    private fun loadAvailableServers() {
        try {
            // assets/servers 디렉토리에서 서버 목록 읽기
            val servers = assets.list("servers") ?: emptyArray()
            availableServers.clear()
            availableServers.addAll(servers.toList())

            Log.d("Servers", "로드된 서버 목록: ${availableServers.joinToString(", ")}")
            
            if (availableServers.isEmpty()) {
                Log.e("Servers", "서버 목록이 비어있습니다. assets/servers 폴더에 서버 디렉토리를 추가해주세요.")
            }
        } catch (e: Exception) {
            Log.e("Servers", "서버 목록 로드 중 오류", e)
            availableServers.clear()
        }
    }
    
    private fun startNodeJS(serverName: String) {
        thread {
            try {
                val nodeDir = copyAndVerifyNodeProject(serverName)
                if (nodeDir == null) {
                    handleError("$serverName 프로젝트 파일 복사 실패")
                    return@thread
                }

                // 실행 권한 설정 (이전 코드 유지)
                setExecutablePermissions(nodeDir)

                // Node.js 실행
                runOnUiThread {
                    _responseText.value = "Node.js 서버 시작 중...\n로그 파일: ${NODE_LOG_FILE}"
                }

                val result = startNodeWithArguments(arrayOf("node", "--trace-warnings", "$nodeDir/main.js"))

                handleNodeStartResult(result)

            } catch (e: Exception) {
                handleError("Node.js 시작 중 예외 발생: ${e.message}")
                e.printStackTrace() // Logcat에 스택 트레이스 출력
            }
        }
    }

    private fun copyAndVerifyNodeProject(serverName: String): String? {
        val nodeDir = "${applicationContext.filesDir.absolutePath}/$serverName"
        try {
            Log.d("NodeJS", "$serverName 프로젝트 복사 시작: $nodeDir")

            // 기존 디렉토리 삭제 후 새로 생성
            File(nodeDir).apply {
                deleteRecursively()
                mkdirs()
            }

            // 소스 경로 결정
            val sourceAssetPath = "servers/$serverName"
            
            // 파일 복사
            if (!copyAssetFolder(sourceAssetPath, nodeDir)) {
                Log.e("NodeJS", "$serverName 프로젝트 파일 복사 실패")
                return null
            }

            // 파일 존재 확인
            val mainJs = File("$nodeDir/main.js")
            if (!mainJs.exists()) {
                Log.e("NodeJS", "main.js 파일이 없습니다")
                return null
            } else {
                Log.d("NodeJS", "main.js 파일 확인 완료")
                return nodeDir
            }

            Log.d("NodeJS", "Node.js 프로젝트 복사 완료")
            return nodeDir
        } catch (e: Exception) {
            Log.e("NodeJS", "프로젝트 복사 중 오류", e)
            return null
        }
    }

    private fun setExecutablePermissions(nodeDir: String) {
        try {
            File(nodeDir).walk().forEach { file ->
                file.setExecutable(true, false)
//                Log.d("NodeJS", "권한 설정: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("NodeJS", "권한 설정 중 오류", e)
        }
    }

    private fun handleNodeStartResult(result: Int) {
        val baseMessage = when (result) {
            0 -> {
                Log.d("NodeJS", "Node.js 서버 시작 함수 반환 (결과 코드 0)")
                // 중요: 결과 코드 0이 반드시 서버 성공을 의미하지 않음.
                // node::Start는 스크립트 실행 후 반환될 수 있음. 실제 서버 상태는 로그 파일 확인 필요.
                "Node.js 프로세스 시작됨 (결과 0). 서버 상태는 로그 파일을 확인하세요."
            }
            -1 -> "Node.js JNI 메모리 할당 실패"
            // node::Start 가 다른 오류 코드를 반환할 수 있음 (문서 확인 필요)
            else -> "Node.js 프로세스 시작 중 오류 발생 (코드: $result). 로그 파일을 확인하세요."
        }

        runOnUiThread {
            _responseText.value = "$baseMessage\n로그 파일: ${NODE_LOG_FILE}"
            // nodeStarted 상태는 테스트 성공 여부나 로그 파일 내용 기반으로 더 정확하게 판단해야 할 수 있음
            // 여기서는 결과 0이면 일단 시작된 것으로 간주
            nodeStarted = (result == 0)
            if(result != 0) nodeStarted = false // 오류 시 확실히 false
        }
    }

    private fun handleError(message: String) {
        Log.e("NodeJS", message)
        runOnUiThread {
            _responseText.value = "$message\nNode.js 로그 파일(${NODE_LOG_FILE})을 확인해보세요."
            nodeStarted = false // 오류 발생 시 false로 설정
        }
    }


    // 나머지 메서드들은 원래 코드 그대로 유지
    private fun startLocalServer() {
        if (localServer == null || !localServer!!.isRunning) {
            localServer = LocalHttpServer(3000)
            localServer!!.setStatusCallback { status ->
                runOnUiThread {
                    _responseText.value = status
                }
            }
            localServer!!.start()
            _responseText.value = "로컬 HTTP 서버 시작 중..."
            localServerStarted = true
        } else {
            _responseText.value = "서버가 이미 실행 중입니다."
        }
    }

    private fun stopLocalServer() {
        localServer?.stop()
        localServer = null
        _responseText.value = "로컬 서버가 중지되었습니다."
        localServerStarted = false
    }

    private fun testNodeServer() {
        runOnUiThread {
            _responseText.value = "서버에 연결 시도 중..."
        }

        thread {
            val urls = listOf(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://10.0.2.2:3000" // 에뮬레이터에서 호스트 PC 접근 시
            )
            var connectionSuccessful = false

            for (urlString in urls) {
                try {
                    Log.d("NodeJS-Test", "$urlString 연결 시도")
                    runOnUiThread {
                        _responseText.value = "$urlString 연결 시도 중..."
                    }

                    val connection = URL(urlString).openConnection() as HttpURLConnection
                    connection.apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        doInput = true
                    }

                    val responseCode = connection.responseCode // 여기서 연결 및 응답 코드 받기
                    Log.d("NodeJS-Test", "$urlString 응답 코드: $responseCode")

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        Log.d("NodeJS-Test", "성공! 응답: $response")
                        runOnUiThread {
                            _responseText.value = "연결 성공($urlString):\n$response"
                        }
                        connectionSuccessful = true
                        break // 성공 시 루프 중단
                    } else {
                        Log.e("NodeJS-Test", "$urlString HTTP 오류 ${connection.responseCode}")
                    }
                    connection.disconnect() // 연결 닫기
                } catch (e: Exception) {
                    Log.e("NodeJS-Test", "연결 실패: $urlString", e)
                }
            }

            if (!connectionSuccessful) {
                runOnUiThread {
                    _responseText.value =
                        "서버 연결 실패.\n서버가 실행 중인지, 방화벽 문제인지 확인하고\nNode.js 로그 파일(${NODE_LOG_FILE})을 확인해보세요."
                }
            }
        }
    }
        
    // assets에서 폴더 복사하는 함수
    private fun copyAssetFolder(srcName: String, dstName: String): Boolean {
        try {
            val files = assets.list(srcName) ?: return false

            if (files.isEmpty()) {
                // 파일인 경우 직접 복사
                return copyAssetFile(srcName, dstName)
            }

            // 폴더가 없으면 생성
            File(dstName).mkdirs()

            // 모든 파일/폴더 복사
            var result = true
            for (file in files) {
                result = result and copyAssetFolder("$srcName/$file", "$dstName/$file")
            }
            return result
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun copyAssetFile(srcName: String, dstName: String): Boolean {
        try {
            assets.open(srcName).use { input ->
                FileOutputStream(dstName).use { output ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}

@Composable
fun ServerSelectionScreen(
    availableServers: List<String>,
    onServerSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "사용할 서버를 선택해주세요",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(availableServers) { serverName ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { onServerSelected(serverName) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = serverName,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServerScreen(
    serverName: String,
    onStartNode: () -> Unit,
    onStartLocalServer: () -> Unit,
    onStopLocalServer: () -> Unit,
    onTestServer: () -> Unit,
    onChangeServer: () -> Unit,
    isNodeStarted: Boolean,
    isLocalServerStarted: Boolean
) {
    // 현재 활성화된 MainActivity 가져오기
    val mainActivity = LocalContext.current as? MainActivity
    // MainActivity의 responseText 값 사용
    val responseText = mainActivity?.responseText?.value ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "서버: $serverName",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = if (isNodeStarted) {
                "Node.js 서버가 실행 중입니다"
            } else if (isLocalServerStarted) {
                "로컬 HTTP 서버가 실행 중입니다"
            } else {
                "서버가 중지되었습니다"
            },
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Node.js 서버 버튼
        Button(
            onClick = onStartNode,
            enabled = !isNodeStarted && !isLocalServerStarted
        ) {
            Text("Node.js 서버 시작")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 로컬 HTTP 서버 버튼들
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStartLocalServer,
                enabled = !isNodeStarted && !isLocalServerStarted
            ) {
                Text("로컬 서버 시작")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onStopLocalServer,
                enabled = isLocalServerStarted
            ) {
                Text("로컬 서버 중지")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 테스트 버튼
            Button(
                onClick = onTestServer,
                enabled = isNodeStarted || isLocalServerStarted
            ) {
                Text("서버 테스트")
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 서버 변경 버튼
            Button(
                onClick = onChangeServer
            ) {
                Text("서버 변경")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 응답 표시 영역
        Text(
            text = responseText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp)
        )
    }
}