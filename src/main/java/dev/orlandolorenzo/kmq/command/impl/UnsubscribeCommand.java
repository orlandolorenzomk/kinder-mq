package dev.orlandolorenzo.kmq.command.impl;

import dev.orlandolorenzo.kmq.command.CommandExecutor;
import dev.orlandolorenzo.kmq.models.SubscriberModel;
import dev.orlandolorenzo.kmq.models.TopicModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class UnsubscribeCommand implements CommandExecutor {

    private final Map<String, SubscriberModel> subscribers;
    private final Map<String, List<SubscriberModel>> topicIndex;

    @Override
    public void execute(Socket clientSocket, List<String> arguments) {
        String subscriberName = arguments.get(1);
        List<String> topicList = Arrays.asList(arguments.get(2).split(","));

        SubscriberModel subscriber = subscribers.get(subscriberName);
        if (subscriber == null) {
            log.error("Subscriber does not exist: {}", subscriberName);
            return;
        }

        subscribers.remove(subscriberName, subscriber);
        topicList.forEach(topic -> {
            List<SubscriberModel> subs = topicIndex.get(topic);
            if (subs != null) subs.remove(subscriber);
        });
        log.info("Subscriber '{}' unsubscribed from topics: {}", subscriberName, topicList);
    }

    @Override
    public void validateArguments(List<String> arguments) {
        if (arguments.size() < 3)       throw new IllegalStateException("UNSUBSCRIBE requires: UNSUBSCRIBE;<subscriberName>;<topic1,topic2...>");
        if (arguments.get(1).isBlank()) throw new IllegalStateException("Subscriber name cannot be blank");
        if (arguments.get(2).isBlank()) throw new IllegalStateException("Topic list cannot be blank");
    }
}
