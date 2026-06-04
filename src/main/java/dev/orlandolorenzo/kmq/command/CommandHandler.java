package dev.orlandolorenzo.kmq.command;

import dev.orlandolorenzo.kmq.command.impl.ReadCommand;
import dev.orlandolorenzo.kmq.command.impl.SubscribeCommand;
import dev.orlandolorenzo.kmq.command.impl.UnsubscribeCommand;
import dev.orlandolorenzo.kmq.command.impl.WriteCommand;
import dev.orlandolorenzo.kmq.models.SubscriberModel;
import dev.orlandolorenzo.kmq.models.TopicModel;
import lombok.RequiredArgsConstructor;

import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
public class CommandHandler {

    private final Map<CommandType, CommandExecutor> executors;

    public CommandHandler(Map<String, SubscriberModel> subscribers, Map<String, TopicModel> topics,
                          Map<String, List<SubscriberModel>> topicIndex, AtomicLong activeVirtualThreads) {
        this.executors = Map.of(
                CommandType.SUBSCRIBE,   new SubscribeCommand(subscribers, topics, topicIndex),
                CommandType.UNSUBSCRIBE, new UnsubscribeCommand(subscribers, topicIndex),
                CommandType.READ,        new ReadCommand(subscribers, topics),
                CommandType.WRITE,       new WriteCommand(topicIndex, activeVirtualThreads)
        );
    }

    public void handle(Socket clientSocket, List<String> arguments) {
        CommandType command = CommandType.valueOf(arguments.getFirst());
        executors.get(command).validateArguments(arguments);
        executors.get(command).execute(clientSocket, arguments);
    }
}
