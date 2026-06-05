# KinderMQ

A lightweight, high-throughput TCP message broker built in Java 21.

Implements a pure **publish/subscribe** model — producers write messages to named topics, all active subscribers receive each message in real time.

---

## Benchmarks

Tested on a single machine (loopback), 5 subscribers, 3 producers, 4 topics:

| Messages sent | Messages delivered | Throughput (recv/s) | Runtime |
|---|---|---|---|
| 1,000,000 | ~2,500,000 | ~300,000 | Java 21 |
| 10,000,000 | ~35,000,000 | ~2,200,000 | Java 21 |
| 100,000,000 | ~200,000,000 | ~1,200,000 | Java 21 |
| 10,000,000 | ~27,500,000 | ~1,668,000 | C23 |

IoT scenario (100 producers, 3 consumers, 10 topics):

| Implementation | Throughput sent/s | Throughput recv/s |
|---|---|---|
| Java 21 | ~566,000 | ~566,000 |

---

## Architecture

```
[Producer]  ──WRITE;topic;payload──▶  [BrokerServer]
                                            │
                                     [CommandHandler]
                                            │
                                     [WriteCommand]
                                            │
                                   queue.offer(payload)
                                            │
                                   [Per-topic drain thread]
                                      queue.take()
                                            │
                              ┌─────────────┴─────────────┐
                         [Subscriber A]            [Subscriber B]
                         BufferedWriter             BufferedWriter
                              │                         │
                         [Flusher thread — every 5ms flushes all subscribers]
```

**Key design decisions:**

- **One virtual thread per client connection** — Java 21 virtual threads handle thousands of concurrent connections with no OS thread overhead
- **One drain thread per topic** — no per-message task submission; producers just `offer()` to a bounded `LinkedBlockingQueue`
- **Reverse index** (`topic → List<Subscriber>`) — O(1) subscriber lookup on broadcast, no full-scan
- **Buffered writes + periodic flush** — `BufferedWriter` per subscriber, flushed every 5ms to batch syscalls
- **No persistence** — pure pub/sub, fire-and-forget

---

## Protocol

Plain TCP, line-delimited (`\n`), semicolon-separated fields.

| Command | Syntax | Description |
|---|---|---|
| `SUBSCRIBE` | `SUBSCRIBE;<name>;<topic1,topic2,...>` | Register and start receiving messages |
| `UNSUBSCRIBE` | `UNSUBSCRIBE;<name>;<topic1,topic2,...>` | Remove registration |
| `WRITE` | `WRITE;<topic>;<payload>` | Publish a message |

### Example

```bash
# Terminal 1 — subscribe
echo "SUBSCRIBE;alice;news,sports" | nc localhost 8090

# Terminal 2 — publish
echo "WRITE;news;breaking news" | nc localhost 8090
```

---

## Getting Started

### Requirements

- Java 21+
- Maven 3.8+

### Build & Run

```bash
mvn package -q
java -jar target/kinder-mq-1.0-SNAPSHOT.jar --config=config.yml
```

### Configuration

```yaml
name: kinder-instance-01
port: 8090
maxClients: 50
maxTopics: 250
maxPayloadBytes: 2048
```

---

## Simulation

A Go-based simulation tool is included in `scripts/simulation.go`.

```bash
# 1M messages — 5 subscribers, 3 producers, 4 topics
go run scripts/simulation.go -clients 5 -producers 3 -topics 4 -messages 334000 -drain 10 -duration 120

# IoT scenario — 100 IoT devices publishing, 3 servers consuming
go run scripts/simulation.go -clients 3 -producers 100 -topics 10 -messages 100000 -drain 15 -duration 300
```

**Flags:**

| Flag | Default | Description |
|---|---|---|
| `-port` | `8090` | Broker port |
| `-clients` | `3` | Number of subscriber clients |
| `-producers` | `2` | Number of producer clients |
| `-topics` | `3` | Number of topics |
| `-messages` | `20` | Messages per producer |
| `-drain` | `2` | Seconds to wait after producers finish |
| `-duration` | `10` | Hard timeout in seconds |

---

## Documentation

Full architecture documentation with diagrams:

```bash
./docs/serve.sh --port=8080
```

Opens at `http://localhost:8080/architecture.html` — includes system architecture, message flow, and connection lifecycle diagrams.

---

## Project Structure

```
kinder-mq/
├── src/                    Java source
│   └── main/java/dev/orlandolorenzo/kmq/
│       ├── broker/         TCP server, connection manager, config parser
│       ├── command/        Command dispatcher and handlers
│       └── models/         Broker, topic, subscriber models
├── c/                      C23 port
│   ├── include/            Header files
│   ├── src/                Source files
│   └── Makefile
├── scripts/
│   └── simulation.go       Benchmark simulation tool
├── docs/
│   ├── architecture.adoc   AsciiDoc source
│   └── serve.sh            Build + serve documentation
└── config.yml              Broker configuration
```
