package dev.orlandolorenzo.kmq.models;

import java.time.Instant;

public record MessageModel (String payload, Instant sentAt) {
}
