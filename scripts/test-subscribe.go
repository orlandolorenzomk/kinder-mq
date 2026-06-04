 package main

import (
    "bufio"
    "fmt"
    "net"
    "os"
)

func main() {
    conn, err := net.Dial("tcp", "localhost:8090")
    if err != nil {
            fmt.Println("Failed to connect:", err)
            os.Exit(1)
    }
    defer conn.Close()

    fmt.Fprintln(conn, "SUBSCRIBE;lorenzo;news,sports")
    fmt.Println("Subscribed, waiting for messages...")

    scanner := bufio.NewScanner(conn)
    for scanner.Scan() {
            fmt.Println("Received:", scanner.Text())
    }
}