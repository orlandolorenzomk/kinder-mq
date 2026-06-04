package dev.orlandolorenzo.kmq.broker;

import dev.orlandolorenzo.kmq.models.BrokerModel;
import lombok.experimental.UtilityClass;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;


@UtilityClass
public class BrokerParser {

    private static final Set<String> KNOWN_KEYS = Set.of("name", "port", "maxClients", "maxTopics", "maxPayloadBytes");

    public static BrokerModel parse(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }

        try (var reader = Files.newBufferedReader(file)) {
            Map<String, Object> raw = new Yaml().load(reader);

            Set<String> unknown = new java.util.HashSet<>(raw.keySet());
            unknown.removeAll(KNOWN_KEYS);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Unknown configuration keys: " + unknown);
            }

            return new BrokerModel(
                    (String) raw.get("name"),
                    (Integer) raw.get("port"),
                    (Integer) raw.get("maxClients"),
                    (Integer) raw.get("maxTopics"),
                    (Integer) raw.get("maxPayloadBytes")
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse YAML file syntax at: " + file, e);
        }
    }
}