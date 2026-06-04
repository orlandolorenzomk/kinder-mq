package main

import (
    "bufio"
    "fmt"
    "net"
    "os"
    "time"
)

func main() {
    conn, err := net.Dial("tcp", "localhost:8090")
    if err != nil {
            fmt.Println("Failed to connect:", err)
            os.Exit(1)
    }
    defer conn.Close()

    fmt.Fprintln(conn, "SUBSCRIBE;lorenzo;news,sports")
    fmt.Println("Subscribed")

    time.Sleep(5 * time.Second)

    fmt.Fprintln(conn, "UNSUBSCRIBE;lorenzo;news,sports")
    fmt.Println("Unsubscribed")

    scanner := bufio.NewScanner(os.Stdin)
    fmt.Println("Press enter to exit")
    scanner.Scan()
}