package dev.orlandolorenzo.kmq.command.impl;

import dev.orlandolorenzo.kmq.command.CommandExecutor;
import dev.orlandolorenzo.kmq.models.SubscriberModel;
import dev.orlandolorenzo.kmq.models.TopicModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.Socket;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class ReadCommand implements CommandExecutor {

    private final Map<String, SubscriberModel> subscribers;
    private final Map<String, TopicModel> topics;

    @Override
    public void execute(Socket clientSocket, List<String> arguments) {
        String subscriberName = arguments.get(1);
        String topicName = arguments.get(2);

        throw new UnsupportedOperationException("READ is not supported in pub/sub mode — use SUBSCRIBE to receive messages");
    }

    @Override
    public void validateArguments(List<String> arguments) {
        if (arguments.size() < 3)       throw new IllegalStateException("READ requires: READ;<subscriberName>;<topicName>");
        if (arguments.get(1).isBlank()) throw new IllegalStateException("Subscriber name cannot be blank");
        if (arguments.get(2).isBlank()) throw new IllegalStateException("Topic name cannot be blank");
    }
}
