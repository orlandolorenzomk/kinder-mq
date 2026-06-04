package dev.orlandolorenzo.kmq.broker;

import dev.orlandolorenzo.kmq.command.CommandHandler;
import dev.orlandolorenzo.kmq.models.BrokerModel;
import dev.orlandolorenzo.kmq.models.SubscriberModel;
import dev.orlandolorenzo.kmq.models.TopicModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
@Slf4j
public class BrokerServer {

    private final BrokerModel brokerModel;
    private final BrokerConnectionManager brokerConnectionManager;

    private static volatile boolean running = true;
    private static final String METRICS_CSV = "metrics.csv";

    private final Map<String, SubscriberModel> subscribers  = new ConcurrentHashMap<>();
    private final Map<String, TopicModel>       topics       = new ConcurrentHashMap<>();
    private final Map<String, List<SubscriberModel>> topicIndex = new ConcurrentHashMap<>();

    private final AtomicLong activeVirtualThreads = new AtomicLong(0);

    private final CommandHandler commandHandler = new CommandHandler(subscribers, topics, topicIndex, activeVirtualThreads);

    public void start() {
        log.info("Attempting to start server...");

        try (var executor     = Executors.newVirtualThreadPerTaskExecutor();
             var serverSocket = new ServerSocket(brokerModel.port())) {

            log.info("Server listening on port: {}", brokerModel.port());

            startFlusher(executor);
            startMetricsLogger(executor);

            while (running) {
                try {
                    var clientSocket = serverSocket.accept();

                    if (!brokerConnectionManager.tryRegister()) {
                        log.warn("Maximum clients reached ({}/{}). Rejecting client.",
                                brokerConnectionManager.getConnectedCount(), brokerModel.maxClients());
                        closeClient(clientSocket, "ERROR: Maximum client limit reached (" + brokerModel.maxClients() + "). Try again later");
                        continue;
                    }

                    log.info("Client connected. Total: {}", brokerConnectionManager.getConnectedCount());
                    submitTracked(executor, () -> handleClient(clientSocket));
                } catch (SocketTimeoutException e) {
                    log.warn("Socket timeout: {}", e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Server error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void submitTracked(ExecutorService executor, Runnable task) {
        activeVirtualThreads.incrementAndGet();
        executor.submit(() -> {
            try {
                task.run();
            } finally {
                activeVirtualThreads.decrementAndGet();
            }
        });
    }

    private void startFlusher(ExecutorService executor) {
        submitTracked(executor, () -> {
            while (running) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                subscribers.forEach((name, sub) -> {
                    try { sub.writer().flush(); }
                    catch (IOException e) { subscribers.remove(name); }
                });
            }
        });
    }

    private void startMetricsLogger(ExecutorService executor) {
        initCsv();
        submitTracked(executor, () -> {
            while (running) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                collectAndLogMetrics();
            }
        });
    }

    private void initCsv() {
        try (var writer = new BufferedWriter(new FileWriter(METRICS_CSV, false))) {
            writer.write("timestamp,heap_used_mb,heap_total_mb,heap_max_mb,platform_threads,virtual_threads,connected_clients");
            writer.newLine();
        } catch (IOException e) {
            log.error("Failed to initialise metrics CSV: {}", e.getMessage());
        }
    }

    private void collectAndLogMetrics() {
        Runtime rt      = Runtime.getRuntime();
        long usedMb     = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long totalMb    = rt.totalMemory() / 1024 / 1024;
        long maxMb      = rt.maxMemory()   / 1024 / 1024;

        ThreadMXBean tmx    = ManagementFactory.getThreadMXBean();
        int platformThreads = tmx.getThreadCount();
        long virtualThreads = activeVirtualThreads.get();
        int connectedClients = brokerConnectionManager.getConnectedCount();

        log.info("── metrics ── heap: {}/{} MB (max {}) | platform threads: {} | virtual threads: {} | clients: {}",
                usedMb, totalMb, maxMb, platformThreads, virtualThreads, connectedClients);

        appendCsv(usedMb, totalMb, maxMb, platformThreads, virtualThreads, connectedClients);
    }

    private void appendCsv(long usedMb, long totalMb, long maxMb,
                            int platformThreads, long virtualThreads, int connectedClients) {
        try (var writer = new BufferedWriter(new FileWriter(METRICS_CSV, true))) {
            writer.write(String.format("%s,%d,%d,%d,%d,%d,%d",
                    Instant.now(), usedMb, totalMb, maxMb, platformThreads, virtualThreads, connectedClients));
            writer.newLine();
        } catch (IOException e) {
            log.error("Failed to write metrics CSV row: {}", e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        try (clientSocket;
             var reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("Received from client {}: {}", clientSocket.getInetAddress(), line);
                List<String> arguments = parseArguments(line);
                commandHandler.handle(clientSocket, arguments);
            }
        } catch (IOException e) {
            log.error("Error while client connecting: {}", e.getMessage());
        } finally {
            brokerConnectionManager.deregister();
        }
    }

    private List<String> parseArguments(String line) {
        return Arrays.asList(line.split(";"));
    }

    private void closeClient(Socket clientSocket, String message) {
        try (clientSocket;
             var writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            writer.println(message);
            writer.flush();
        } catch (IOException e) {
            log.error("Failed to send rejection message to client", e);
        } finally {
            brokerConnectionManager.deregister();
        }
    }
}
