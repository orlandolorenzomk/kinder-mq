package dev.orlandolorenzo.kmq.models;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.stream.Collectors;

public record BrokerModel(
        @NotBlank String name,
        @Min(1024) @Max(65535) int port,
        @Positive int maxClients,
        @Positive int maxTopics,
        @Positive int maxPayloadBytes
) {
    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    public BrokerModel(String name, int port, int maxClients, int maxTopics, int maxPayloadBytes) {
        this.name = name;
        this.port = port;
        this.maxClients = maxClients;
        this.maxTopics = maxTopics;
        this.maxPayloadBytes = maxPayloadBytes;

        var violations = VALIDATOR.validate(this);

        if (!violations.isEmpty()) {
            String errorMsg = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Invalid broker configuration: " + errorMsg);
        }
    }
}