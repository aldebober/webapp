package main

import (
	"fmt"
	"net/http"
)

func main() {
	http.HandleFunc("/", handleRequest)
	http.ListenAndServe(":8089", nil)
}

func handleRequest(w http.ResponseWriter, r *http.Request) {
	if r.Method == "POST" && r.Header.Get("NotBad") == "true" {
		fmt.Fprint(w, "ReallyNotBad")
	} else {
		http.Error(w, "Invalid request", http.StatusBadRequest)
	}
}

