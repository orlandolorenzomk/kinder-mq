package dev.orlandolorenzo.kmq.command.impl;

import dev.orlandolorenzo.kmq.command.CommandExecutor;
import dev.orlandolorenzo.kmq.models.SubscriberModel;
import dev.orlandolorenzo.kmq.models.TopicModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RequiredArgsConstructor
@Slf4j
public class SubscribeCommand implements CommandExecutor {

    private final Map<String, SubscriberModel> subscribers;
    private final Map<String, TopicModel> topics;
    private final Map<String, List<SubscriberModel>> topicIndex;

    @Override
    public void execute(Socket clientSocket, List<String> arguments) {
        List<String> tempTopics = Arrays.asList(arguments.get(2).split(","));
        String subscriberName = arguments.get(1);

        try {
            Set<String> resolvedTopics = resolveTopic(tempTopics);
            SubscriberModel subscriberModel = SubscriberModel
                    .builder()
                    .name(subscriberName)
                    .topics(resolvedTopics)
                    .loginAt(Instant.now())
                    .writer(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())))
                    .build();
            subscribers.put(subscriberName, subscriberModel);
            resolvedTopics.forEach(topic ->
                    topicIndex.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(subscriberModel));
            log.info("Subscriber '{}' registered on topics: {}", subscriberName, resolvedTopics);
        } catch (IOException e) {
            log.error("Failed to create writer for subscriber {}: {}", subscriberName, e.getMessage());
        }
    }

    private Set<String> resolveTopic(List<String> topicList) {
        Set<String> output = new HashSet<>();
        for (String topicName : topicList) {
            topics.computeIfAbsent(topicName, k -> {
                log.info("Topic '{}' created", k);
                return new TopicModel(k, Instant.now());
            });
            output.add(topicName);
        }
        return output;
    }

    @Override
    public void validateArguments(List<String> arguments) {
        if (arguments.size() < 3)       throw new IllegalStateException("SUBSCRIBE requires: SUBSCRIBE;<subscriberName>;<topic1,topic2...>");
        if (arguments.get(1).isBlank()) throw new IllegalStateException("Subscriber name cannot be blank");
        if (arguments.get(2).isBlank()) throw new IllegalStateException("Topic list cannot be blank");
    }
}
