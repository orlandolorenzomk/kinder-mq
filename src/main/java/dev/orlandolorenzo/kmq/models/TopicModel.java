package dev.orlandolorenzo.kmq.models;

import java.time.Instant;

public record TopicModel(
        String name,
        Instant createdAt
) {
}
