package shinji;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.util.*;
import java.util.stream.Collectors;

import static io.vertx.core.http.impl.HttpClientConnection.log;


public class SocketServer extends AbstractVerticle {

    private static final Set<ServerWebSocket> connectedClients = ConcurrentHashMap.newKeySet();
    private static final Map<ServerWebSocket, Integer> packetsSentByServer = new ConcurrentHashMap<>();
    private static int totalPacketsSentSinceStart = 0;
    private static int sessionPacketsSent = 0;
    private static final int DUPLICATE_PACKETS = 100;
    private static final int PORT = 9001;
    private static final String URL = "localhost";

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(SocketServer.class.getName(), new DeploymentOptions().setInstances(1));
    }

    @Override
    public void start() {
        HttpServer server = vertx.createHttpServer();
        vertx.exceptionHandler(e -> {
            log.error("Error in Vertx: ", e);
        });

        vertx.setPeriodic(1000, id -> printServerStatus());
        // 강제 종료 시 수행될 로직
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("================ FORCE TERMINATED !! =======================");
            printServerStatus();
        }));

        server.webSocketHandler(webSocket -> {
            //      System.out.println("Connected!");
            connectedClients.add(webSocket);
            packetsSentByServer.put(webSocket, 0);


            webSocket.exceptionHandler(e -> {
                log.error("Error in WebSocket for client " + webSocket.remoteAddress() + ": ", e);
            });

            // 클라이언트로부터 메시지를 받는 핸들러 설정
            webSocket.handler(data -> {
                String message = data.toString();
                // 모든 클라이언트에게 메시지를 전달
                List<ServerWebSocket> allClients = getAllClients();
//        System.out.println("allClients.size() : "+ allClients.size());
                for (ServerWebSocket client : allClients) {
                    client.writeTextMessage("Message from client: " + message);
                    packetsSentByServer.put(client, packetsSentByServer.getOrDefault(client, 0) + 1);
                    totalPacketsSentSinceStart++;
                    sessionPacketsSent++;
                }
            });

            webSocket.closeHandler(v -> {
                log.info("WebSocket connection closed with " + webSocket.remoteAddress());
                connectedClients.remove(webSocket);
                packetsSentByServer.remove(webSocket);
            });

        }).listen(PORT, URL, res -> {
            if (res.succeeded()) {
                System.out.println("Server is now listening on " + PORT);
            } else {
                System.out.println("Failed to bind!");
            }
        });
    }

    private static void printServerStatus() {
        System.out.println("----- Server Packet Report -----");
//    System.out.println("  Clients connected:             " + connectedClients.size());
        System.out.println("  Total packets sent since start: " + totalPacketsSentSinceStart);
        System.out.println("  Session packets sent: " + sessionPacketsSent);
        sessionPacketsSent = 0;
    }

    private static List<ServerWebSocket> getRandomClients(ServerWebSocket including, int maxClients) {
        List<ServerWebSocket> allClients = new ArrayList<>(connectedClients);

        Collections.shuffle(allClients);
        return allClients.subList(0, Math.min(allClients.size(), maxClients));
    }

    private static List<ServerWebSocket> getAllClients() {
        return new ArrayList<>(connectedClients);
    }
}