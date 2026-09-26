#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

POSTGRES_HOST="localhost"
POSTGRES_PORT="5432"
DB_USER="meterflow"

echo "Checking PostgreSQL..."

if ! pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" >/dev/null 2>&1; then
    echo "ERROR: PostgreSQL is not running on $POSTGRES_HOST:$POSTGRES_PORT"
    exit 1
fi

echo "PostgreSQL is running."

for DB_NAME in meterflow meterflow_test; do
    if ! psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" \
        -U "$DB_USER" -d postgres \
        -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1; then

        echo "ERROR: Database '$DB_NAME' does not exist."
        echo "Create it with:"
        echo "  createdb -h $POSTGRES_HOST -p $POSTGRES_PORT -U $DB_USER $DB_NAME"
        exit 1
    fi

    echo "Database '$DB_NAME' exists."
done

echo "Starting MeterFlow..."

./mvnw spring-boot:run -Dspring-boot.run.profiles=local