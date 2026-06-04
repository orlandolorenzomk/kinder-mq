package dev.orlandolorenzo.kmq.broker;

import java.util.concurrent.atomic.AtomicInteger;

public class BrokerConnectionManager {

    private static AtomicInteger connectedClients = new AtomicInteger(0);
    private final int maxClients;

    public BrokerConnectionManager(int maxClients) {
        this.maxClients = maxClients;
    }

    public boolean tryRegister() {
        return connectedClients.incrementAndGet() <= maxClients;
    }

    public void deregister() {
        connectedClients.decrementAndGet();
    }

    public int getConnectedCount() {
        return connectedClients.get();
    }
}


