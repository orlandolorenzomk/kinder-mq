package dev.orlandolorenzo.kmq.command;

import lombok.Getter;

@Getter
public enum CommandType {
    SUBSCRIBE("SUBSCRIBE"),
    UNSUBSCRIBE("UNSUBSCRIBE"),
    WRITE("WRITE"),
    READ("READ");

    private final String value;

    CommandType(String value) {
        this.value = value;
    }
}
