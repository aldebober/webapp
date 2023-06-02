# Start from the latest Alpine base image
FROM golang:alpine as builder

# Set the working directory inside the container
WORKDIR /build

# Copy the Go module files
COPY go.mod ./

# Download and install Go dependencies
RUN go mod download

# Copy the rest of the application source code
COPY ./webapp.go .

# Build the Go application
RUN go build -o myapp

# Let's reduce an image size a bit
FROM alpine:lates

WORKDIR /app
COPY --from=builder /build/myapp /app

# Create a non-root user
RUN adduser -D -g '' gouser

# Set the ownership of the application directory to the non-root user
RUN chown -R gouser:gouser /app

# Switch to the non-root user
USER gouser

# Set the container entry point
ENTRYPOINT ["./myapp"]

