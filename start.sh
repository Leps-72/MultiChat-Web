#!/bin/sh
echo "[STARTUP] Waiting for PostgreSQL..."
sleep 5 # Give some time for DB to be ready, though Render usually provisions it first.

# Execute SQL file to initialize the database
if [ -n "$DB_HOST" ] && [ -n "$DB_USER" ] && [ -n "$DB_NAME" ]; then
    echo "[STARTUP] Initializing database schema..."
    export PGPASSWORD=$DB_PASS
    psql -h $DB_HOST -U $DB_USER -d $DB_NAME -f init_db.sql || echo "[STARTUP] Database already initialized or initialization failed."
else
    echo "[STARTUP] Skipping database initialization because DB connection vars are not set."
fi

echo "[STARTUP] Starting Java MultiChat Server..."
exec java -cp /app/classes:/app/lib/postgresql-42.7.1.jar -Dcom.sun.net.httpserver.maxConnections=1000 -Xmx512m multichat.MultiChatServer
