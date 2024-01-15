package shinji;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class SocketClient {
    private static final String URL = "localhost";
    private static final int MAX_CLIENTS = 80;
    private static final int CLIENT_CREATION_INTERVAL_IN_MS = 100;
//    private static final int EMIT_INTERVAL_IN_MS = 25;
    private static final int EMIT_INTERVAL_IN_MS = 30;
//    private static final int EMIT_INTERVAL_IN_MS = 33;
    //    private static final int EMIT_INTERVAL_IN_MS = 50;
    //  private static final int EMIT_INTERVAL_IN_MS = 1000;
    private static final String PAYLOAD = "A".repeat(5 * 1024);
    private static final AtomicInteger connectedClientsCount = new AtomicInteger(0);
    private static final AtomicInteger createdClientsCount = new AtomicInteger(0);
    private static final AtomicInteger packetsSentSinceLastReport = new AtomicInteger(0);
    private static final AtomicInteger packetsReceivedFromServer = new AtomicInteger(0);
    private static final AtomicInteger totalPacketsReceivedSinceStart = new AtomicInteger(0);
    private static long totalResponseTime = 0;
    private static long maxResponseTime = 0;
    private static final int PORT = 9001;

    private static final Vertx vertx = Vertx.vertx();
    private static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);

    public static void main(String[] args) {
        createClient();
        executorService.scheduleAtFixedRate(SocketClient::printReport, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public static void createClient() {
        vertx.createHttpClient().webSocket(PORT, URL, "/", websocketRes -> {
            /** 소켓 연결 실패*/
            if (websocketRes.failed()) {
                System.out.println("WebSocket connection failed: " + websocketRes.cause().getMessage());
                printReportAndExit();
//                return;
            }
            /** 소켓 연결 성공*/
            // TODO : 아래 두 코드가 동일한지 cGPT에게 물어보기
//            else if (!websocketRes.failed()) {
            else if (websocketRes.succeeded()) {
                connectedClientsCount.incrementAndGet();
                WebSocket websocket = websocketRes.result();

                /** 핸들러 : 메시지 수신*/
                websocket.handler(buffer -> {
//              String receivedMessage = buffer.toString();
//              System.out.println("  receivedMessage");

                    // Handle the received string message. If you need to parse any data from it, you can do it here.
                    packetsReceivedFromServer.incrementAndGet();
                    totalPacketsReceivedSinceStart.incrementAndGet();

                    // responseTime 계산
                    String receivedMessage = buffer.toString();
                    long serverTimestamp = Long.parseLong(receivedMessage.split(", Timestamp: ")[1]);
                    long responseTime = System.currentTimeMillis() - serverTimestamp;

                    // responseTime 업데이트
                    totalResponseTime += responseTime;
                    if (responseTime > maxResponseTime) maxResponseTime = responseTime;
                });

                /** 핸들러 : 예외 처리*/
                websocket.exceptionHandler(err -> {
                    System.out.println("Error in connection: " + err.getMessage());
                    printReportAndExit();
                });

                /** 핸들러 : 소켓 끊김*/
                websocket.closeHandler(v -> {
                    connectedClientsCount.decrementAndGet();
                    System.out.println("WebSocket connection closed.");
                    printReportAndExit();
                });

                /** 스케줄러 : 메시지 송신*/
                executorService.scheduleAtFixedRate(() -> {
                    String payload = "Message: " + PAYLOAD + ", Timestamp: " + System.currentTimeMillis();
                    websocket.writeTextMessage(payload);
                    packetsSentSinceLastReport.incrementAndGet();
                }, 0, EMIT_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);

                if (createdClientsCount.incrementAndGet() < MAX_CLIENTS) {
                    vertx.setTimer(CLIENT_CREATION_INTERVAL_IN_MS, timerId -> createClient());
                }
            }
        });
    }

    public static void printReport() {
        // 평균 응답 시간과 최대 응답 시간 계산
        double averageResponseTimeMs = totalPacketsReceivedSinceStart.get() > 0 ?
                (double) totalResponseTime / totalPacketsReceivedSinceStart.get() : 0;
        double averageResponseTimeS = averageResponseTimeMs / 1000;
        double maxResponseTimeS = (double) maxResponseTime / 1000;

        System.out.println("----- Client Report -----");
        System.out.println("  Numbers of created clients : " + createdClientsCount.get());
        System.out.println("  Numbers of connected clients : " + connectedClientsCount.get());
        System.out.println("  Total packets received    : " + totalPacketsReceivedSinceStart.get());
        System.out.println("  Session packets sent      : " + packetsSentSinceLastReport.getAndSet(0));
        System.out.println("  Session packets received  : " + packetsReceivedFromServer.getAndSet(0));
        System.out.printf("  Average response time: %.2f ms (%.2f s)\n", averageResponseTimeMs, averageResponseTimeS);
        System.out.printf("  Max response time: %d ms (%.2f s)\n", maxResponseTime, maxResponseTimeS);
        maxResponseTime = 0;
    }

    public static void printReportAndExit() {
        System.out.println("================ FORCE TERMINATED !! =======================");
        printReport();
        executorService.shutdownNow(); // 모든 실행중인 작업을 중단하고 스케쥴링된 작업들을 모두 취소합니다.
        vertx.close();
        System.exit(0);
    }
}
