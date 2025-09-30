#!/bin/sh
DIR="$(dirname "$0")"
exec "$DIR/services/players-api/mvnw" "$@"
