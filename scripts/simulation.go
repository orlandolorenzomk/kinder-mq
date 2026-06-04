package main

import (
	"bufio"
	"flag"
	"fmt"
	"math/rand"
	"net"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const host = "localhost"

func subscriber(name string, topics []string, port int, ready chan<- struct{}, stop <-chan struct{}, received *int64) {
	conn, err := net.Dial("tcp", fmt.Sprintf("%s:%d", host, port))
	if err != nil {
		fmt.Printf("subscriber %s connect error: %v\n", name, err)
		ready <- struct{}{}
		return
	}

	fmt.Fprintf(conn, "SUBSCRIBE;%s;%s\n", name, strings.Join(topics, ","))
	ready <- struct{}{}

	done := make(chan struct{})
	go func() {
		defer close(done)
		scanner := bufio.NewScanner(conn)
		scanner.Buffer(make([]byte, 256*1024), 256*1024)
		for scanner.Scan() {
			atomic.AddInt64(received, 1)
		}
	}()

	select {
	case <-stop:
		conn.Close()
		<-done
	case <-done:
	}
}

func producer(name string, topics []string, messages int, port int, wg *sync.WaitGroup, sent *int64) {
	defer wg.Done()
	conn, err := net.Dial("tcp", fmt.Sprintf("%s:%d", host, port))
	if err != nil {
		fmt.Printf("producer %s connect error: %v\n", name, err)
		return
	}
	defer conn.Close()

	w := bufio.NewWriterSize(conn, 256*1024)
	for i := 0; i < messages; i++ {
		topic := topics[rand.Intn(len(topics))]
		ts := float64(time.Now().UnixNano()) / 1e9
		fmt.Fprintf(w, "WRITE;%s;{\"producer\":\"%s\",\"msg\":%d,\"ts\":%.6f}\n", topic, name, i, ts)
	}
	w.Flush()
	atomic.AddInt64(sent, int64(messages))
}

func randomSample(topics []string, k int) []string {
	perm := rand.Perm(len(topics))
	out := make([]string, k)
	for i := range out {
		out[i] = topics[perm[i]]
	}
	return out
}

func main() {
	port      := flag.Int("port",      8090, "broker port")
	clients   := flag.Int("clients",   3,     "number of subscriber clients")
	producers := flag.Int("producers", 2,     "number of producer clients")
	topics    := flag.Int("topics",    3,     "number of topics")
	messages  := flag.Int("messages",  20,    "messages per producer")
	duration  := flag.Int("duration",  10,    "hard timeout in seconds")
	drain     := flag.Int("drain",     2,     "seconds to wait for in-flight messages after producers finish")
	flag.Parse()

	topicList := make([]string, *topics)
	for i := range topicList {
		topicList[i] = fmt.Sprintf("topic:%d", i)
	}

	stop := make(chan struct{})
	var closeOnce sync.Once
	closeFn := func() { closeOnce.Do(func() { close(stop) }) }

	readyCh := make(chan struct{}, *clients)
	subStats := make([]int64, *clients)
	var subWg sync.WaitGroup

	for i := 0; i < *clients; i++ {
		subWg.Add(1)
		i := i
		assigned := randomSample(topicList, rand.Intn(len(topicList))+1)
		go func() {
			defer subWg.Done()
			subscriber(fmt.Sprintf("sub%d", i+1), assigned, *port, readyCh, stop, &subStats[i])
		}()
	}

	for range *clients {
		<-readyCh
	}

	start := time.Now()

	prodStats := make([]int64, *producers)
	var prodWg sync.WaitGroup

	for i := 0; i < *producers; i++ {
		prodWg.Add(1)
		i := i
		go producer(fmt.Sprintf("prod%d", i+1), topicList, *messages, *port, &prodWg, &prodStats[i])
	}

	go func() {
		prodWg.Wait()
		time.Sleep(time.Duration(*drain) * time.Second)
		closeFn()
	}()

	select {
	case <-stop:
	case <-time.After(time.Duration(*duration) * time.Second):
		closeFn()
	}

	subWg.Wait()
	elapsed := time.Since(start).Seconds()

	var totalSent, totalRecv int64
	for _, v := range prodStats {
		totalSent += v
	}
	for _, v := range subStats {
		totalRecv += v
	}

	fmt.Printf("\n========== STATS ==========\n")
	fmt.Printf("  duration:           %.2fs\n", elapsed)
	fmt.Printf("  total sent:         %d\n", totalSent)
	fmt.Printf("  total received:     %d\n", totalRecv)
	fmt.Printf("  messages/sec sent:  %.0f\n", float64(totalSent)/elapsed)
	fmt.Printf("  messages/sec recv:  %.0f\n", float64(totalRecv)/elapsed)
	fmt.Println()
	for i, v := range prodStats {
		fmt.Printf("  prod%d: %d sent\n", i+1, v)
	}
	for i, v := range subStats {
		fmt.Printf("  sub%d:  %d recv\n", i+1, v)
	}
	fmt.Println("===========================")

	fmt.Println("\n=== UNSUBSCRIBE ALL ===")
	topicStr := strings.Join(topicList, ",")
	for i := range *clients {
		conn, err := net.Dial("tcp", fmt.Sprintf("%s:%d", host, *port))
		if err == nil {
			fmt.Fprintf(conn, "UNSUBSCRIBE;sub%d;%s\n", i+1, topicStr)
			conn.Close()
		}
	}
	fmt.Println("done")
}
