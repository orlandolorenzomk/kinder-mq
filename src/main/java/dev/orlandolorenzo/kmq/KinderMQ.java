package dev.orlandolorenzo.kmq;

import dev.orlandolorenzo.kmq.broker.BrokerConnectionManager;
import dev.orlandolorenzo.kmq.broker.BrokerParser;
import dev.orlandolorenzo.kmq.broker.BrokerServer;
import dev.orlandolorenzo.kmq.models.BrokerModel;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public class KinderMQ {

    public static void main(String[] args) {
        log.info("Starting KinderMQ");

        String configPath = args[0];

        if (configPath == null)
            throw new IllegalStateException("--config argument is required");

        Path path = Path.of(configPath.substring(9));
        BrokerModel brokerModel = BrokerParser.parse(path);

        BrokerConnectionManager brokerConnectionManager = new BrokerConnectionManager(brokerModel.port());

        BrokerServer brokerServer = new BrokerServer(brokerModel, brokerConnectionManager);
        brokerServer.start();
    }
}
