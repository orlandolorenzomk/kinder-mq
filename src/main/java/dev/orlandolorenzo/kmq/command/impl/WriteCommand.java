package dev.orlandolorenzo.kmq.command.impl;

import dev.orlandolorenzo.kmq.command.CommandExecutor;
import dev.orlandolorenzo.kmq.models.SubscriberModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
@Slf4j
public class WriteCommand implements CommandExecutor {

    private static final int QUEUE_CAPACITY = 100_000;

    private final Map<String, List<SubscriberModel>> topicIndex;
    private final AtomicLong activeVirtualThreads;

    private final Map<String, BlockingQueue<String>> queues = new ConcurrentHashMap<>();

    @Override
    public void execute(Socket clientSocket, List<String> arguments) {
        String topicName = arguments.get(1);
        String payload   = arguments.get(2);

        BlockingQueue<String> queue = queues.computeIfAbsent(topicName, k -> {
            var q = new LinkedBlockingQueue<String>(QUEUE_CAPACITY);
            startDrainThread(k, q);
            return q;
        });

        try {
            queue.put(payload);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void startDrainThread(String topicName, BlockingQueue<String> queue) {
        activeVirtualThreads.incrementAndGet();
        Thread.ofVirtual().start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String payload = queue.take();
                    drain(topicName, payload);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                activeVirtualThreads.decrementAndGet();
            }
        });
    }

    @SuppressWarnings("resource")
    private void drain(String topicName, String payload) {
        List<SubscriberModel> targets = topicIndex.getOrDefault(topicName, List.of());
        for (SubscriberModel subscriber : targets) {
            try {
                subscriber.writer().write(payload);
                subscriber.writer().newLine();
            } catch (IOException e) {
                log.error("Failed to deliver to subscriber '{}', removing", subscriber.name());
                targets.remove(subscriber);
            }
        }
    }

    @Override
    public void validateArguments(List<String> arguments) {
        if (arguments.size() < 3)       throw new IllegalStateException("WRITE requires: WRITE;<topicName>;<payload>");
        if (arguments.get(1).isBlank()) throw new IllegalStateException("Topic name cannot be blank");
        if (arguments.get(2).isBlank()) throw new IllegalStateException("Payload cannot be blank");
    }
}
