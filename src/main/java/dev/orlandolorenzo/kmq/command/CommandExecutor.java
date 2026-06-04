package dev.orlandolorenzo.kmq.command;

import java.net.Socket;
import java.util.List;

public interface CommandExecutor {

    void execute(Socket clientSocket, List<String> arguments);
    void validateArguments(List<String> arguments);
}
