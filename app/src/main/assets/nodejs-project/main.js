const http = require('http');
const os = require('os');
const fs = require('fs');
const path = require('path'); // 경로 처리를 위해 추가

// !!! 중요: 앱의 패키지 이름과 일치해야 합니다 !!!
const logFilePath = '/data/data/com.example.mobilecontextserver/files/node_log.log';

// 로그 파일이 저장될 디렉토리 생성 (없을 경우)
const logDir = path.dirname(logFilePath);
if (!fs.existsSync(logDir)) {
    try {
        fs.mkdirSync(logDir, { recursive: true });
    } catch (err) {
        // 디렉토리 생성 실패 시 stderr로 출력 시도 (Logcat에 보이지 않을 수 있음)
        process.stderr.write(`Failed to create log directory: ${err}\n`);
    }
}

// 파일에 로그를 기록하는 함수
function logToFile(message) {
    try {
        const timestamp = new Date().toISOString();
        let logMessage = `${timestamp}: ${message}`;

        // 오류 객체인 경우 스택 트레이스를 포함
        if (message instanceof Error) {
            logMessage = `${timestamp}: ERROR: ${message.stack || message}`;
        }

        fs.appendFileSync(logFilePath, logMessage + '\n', 'utf8');
    } catch (err) {
        // 파일 로깅 자체 실패 시 stderr로 출력 시도
        process.stderr.write(`Failed to write to log file (${logFilePath}): ${err}\n`);
        process.stderr.write(`Original message: ${message}\n`);
    }
}

// --- Node.js 세션 시작 로그 ---
logToFile('--- Node.js Session Started ---');
logToFile(`Log file path: ${logFilePath}`);
logToFile('Node.js server starting...');

// 처리되지 않은 예외 발생 시 파일에 로그 기록
process.on('uncaughtException', (err) => {
  logToFile(err); // 오류 객체를 직접 전달하여 스택 트레이스 포함
  // 필요하다면 프로세스 종료: process.exit(1);
});

// 처리되지 않은 Promise 거부 시 파일에 로그 기록
process.on('unhandledRejection', (reason, promise) => {
  logToFile(`Unhandled Rejection at: ${promise}, reason: ${reason.stack || reason}`);
  // 필요하다면 프로세스 종료: process.exit(1);
});


// 서버 IP 주소 가져오기 함수 (변경 없음)
function getServerIPs() {
    const interfaces = os.networkInterfaces();
    const addresses = [];
    Object.keys(interfaces).forEach(interfaceName => {
        interfaces[interfaceName].forEach(iface => {
            if (iface.family === 'IPv4' && !iface.internal) {
                addresses.push(iface.address);
            }
        });
    });
    addresses.push('127.0.0.1');
    addresses.push('localhost');
    return addresses;
}

// HTTP 서버 생성 (변경 없음)
const server = http.createServer((req, res) => {
    logToFile(`Incoming request: ${req.method} ${req.url}`); // 요청 로그 추가
    res.writeHead(200, {'Content-Type': 'application/json'});
    const data = {
        message: "Node.js 서버가 작동 중입니다!",
        timestamp: new Date().toISOString(),
        serverIPs: getServerIPs(),
        serverPort: 3000
    };
    res.end(JSON.stringify(data));
});

// 서버 리스닝
const PORT = 3000;
try {
    server.listen(PORT, '0.0.0.0', () => {
        logToFile(`Server listening on 0.0.0.0:${PORT}`);
        logToFile(`Available addresses:`);
        getServerIPs().forEach(ip => {
            logToFile(`  http://${ip}:${PORT}`);
        });
    });

    // 서버 오류 발생 시 로그 기록
    server.on('error', (err) => {
        logToFile(`Server error: ${err.stack || err}`);
        // 필요 시 프로세스 종료 또는 다른 처리
    });

} catch (err) {
    logToFile(`Failed to start server: ${err.stack || err}`);
    process.exit(1); // 서버 시작 실패 시 종료
}