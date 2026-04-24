# Multi-stage build for MultiChat - Java Chat Application

# Stage 1: Build with Maven/Ant
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Install build tools
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy source and web files
COPY src ./src
COPY web ./web

# Download PostgreSQL JDBC driver
RUN mkdir -p lib build/classes && \
    curl -L https://jdbc.postgresql.org/download/postgresql-42.7.1.jar -o lib/postgresql-42.7.1.jar

# Compile the project directly with javac
RUN javac -d build/classes -sourcepath src -cp lib/postgresql-42.7.1.jar src/multichat/*.java

# Stage 2: Runtime
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install runtime dependencies
RUN apt-get update && apt-get install -y curl postgresql-client && rm -rf /var/lib/apt/lists/*

# Copy compiled classes and resources from builder
COPY --from=builder /app/build/classes ./classes
COPY --from=builder /app/lib ./lib
COPY --from=builder /app/web ./web
COPY init_db.sql ./
COPY start.sh ./
RUN sed -i 's/\r$//' start.sh && chmod +x start.sh


# Set CLASSPATH to include PostgreSQL driver
ENV CLASSPATH=/app/classes:/app/lib/*:/app/lib/postgresql-42.7.1.jar

# Expose ports
EXPOSE 8080

# Set environment variables for cloud deployment
ENV PORT=8080 \
    DB_TYPE=postgresql \
    DB_HOST=localhost \
    DB_PORT=5432 \
    DB_NAME=multichat \
    DB_USER=multichat \
    DB_PASS=multichat123 \
    JAVA_OPTS="-Dcom.sun.net.httpserver.maxConnections=1000 -Xmx512m"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

# Run application via startup script
CMD ["/app/start.sh"]
