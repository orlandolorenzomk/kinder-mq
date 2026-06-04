package dev.orlandolorenzo.kmq.models;

import lombok.Builder;

import java.io.BufferedWriter;
import java.time.Instant;
import java.util.Set;

@Builder
public record SubscriberModel(
        String name,
        Instant loginAt,
        Instant lastMessageFiredAt,
        Set<String> topics,
        BufferedWriter writer
) {
}
