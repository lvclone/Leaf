#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$SERVER_DIR/.." && pwd)"
ENV_FILE="${LEAF_ENV_FILE:-$SERVER_DIR/.env}"
TEST_DIR="$(mktemp -d "${TMPDIR:-/tmp}/leaf-segment-test.XXXXXX")"
SERVER_PIDS=()

UNIQUE_REQUESTS="${LEAF_UNIQUE_REQUESTS:-10000}"
UNIQUE_CONCURRENCY="${LEAF_UNIQUE_CONCURRENCY:-100}"
RESTART_REQUESTS="${LEAF_RESTART_REQUESTS:-100}"
RECOVERY_REQUESTS="${LEAF_RECOVERY_REQUESTS:-5000}"
MULTI_INSTANCE_REQUESTS="${LEAF_MULTI_INSTANCE_REQUESTS:-5000}"
MULTI_INSTANCE_CONCURRENCY="${LEAF_MULTI_INSTANCE_CONCURRENCY:-100}"
LONG_SECONDS="${LEAF_LONG_SECONDS:-60}"
LONG_BATCH_REQUESTS="${LEAF_LONG_BATCH_REQUESTS:-20000}"
LONG_CONCURRENCY="${LEAF_LONG_CONCURRENCY:-100}"
TEST_KEY="${LEAF_TEST_KEY:-leaf-segment-test}"
SINGLE_PORT="${LEAF_SINGLE_PORT:-18080}"
SECOND_PORT="${LEAF_SECOND_PORT:-18081}"
ENDPOINT_PATH="/api/segment/get/$TEST_KEY"

cleanup() {
    if ((${#SERVER_PIDS[@]})); then
        for pid in "${SERVER_PIDS[@]}"; do
            kill "$pid" 2>/dev/null || true
        done
        for pid in "${SERVER_PIDS[@]}"; do
            wait "$pid" 2>/dev/null || true
        done
    fi
    printf 'Test artifacts: %s\n' "$TEST_DIR"
}

trap cleanup EXIT INT TERM

if [[ ! -f "$ENV_FILE" ]]; then
    printf 'Missing environment file: %s\n' "$ENV_FILE" >&2
    exit 1
fi

set -a
. "$ENV_FILE"
set +a

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$SERVER_DIR/docker-compose.yml")
JAR_FILE="$SERVER_DIR/target/leaf.jar"

require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        printf 'Required command not found: %s\n' "$1" >&2
        exit 1
    }
}

for command_name in docker mvn java curl ab awk sort uniq xargs; do
    require_command "$command_name"
done

wait_for_endpoint() {
    local url="$1"
    local attempt
    for attempt in $(seq 1 60); do
        if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    printf 'Service did not become ready: %s\n' "$url" >&2
    exit 1
}

start_server() {
    local port="$1"
    local log_file="$TEST_DIR/server-$port.log"
    SERVER_PORT="$port" java -jar "$JAR_FILE" --server.port="$port" >"$log_file" 2>&1 &
    local pid=$!
    SERVER_PIDS+=("$pid")
    wait_for_endpoint "http://127.0.0.1:$port$ENDPOINT_PATH"
    printf 'Started server port=%s pid=%s log=%s\n' "$port" "$pid" "$log_file"
    LAST_PID="$pid"
}

stop_server() {
    local pid="$1"
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
}

request_ids() {
    local url="$1"
    local request_count="$2"
    local concurrency="$3"
    local output_file="$4"
    local result_dir="$TEST_DIR/results-$(basename "$output_file")"
    local result_file

    mkdir -p "$result_dir"
    LEAF_TEST_URL="$url" LEAF_RESULT_DIR="$result_dir" xargs -P "$concurrency" -n 1 sh -c \
        'request_id="$1"; if response="$(curl -fsS --max-time 10 "$LEAF_TEST_URL" 2>/dev/null)"; then printf "%s\n" "$response" > "$LEAF_RESULT_DIR/$request_id"; else printf "ERROR\n" > "$LEAF_RESULT_DIR/$request_id"; fi' _ \
        < <(seq 1 "$request_count")
    : > "$output_file"
    for result_file in "$result_dir"/*; do
        if [[ -f "$result_file" ]]; then
            cat "$result_file" >> "$output_file"
        fi
    done
}

assert_unique_ids() {
    local label="$1"
    local input_file="$2"
    local expected_count="$3"
    local numeric_count
    local error_count
    local unique_count

    numeric_count="$(awk '/^[0-9]+$/ { count++ } END { print count + 0 }' "$input_file")"
    error_count="$(awk '$0 == "ERROR" { count++ } END { print count + 0 }' "$input_file")"
    unique_count="$(awk '/^[0-9]+$/ { print }' "$input_file" | sort -n -u | wc -l | tr -d ' ')"

    printf '%s: expected=%s numeric=%s unique=%s errors=%s\n' \
        "$label" "$expected_count" "$numeric_count" "$unique_count" "$error_count"

    if [[ "$numeric_count" -ne "$expected_count" || "$unique_count" -ne "$expected_count" || "$error_count" -ne 0 ]]; then
        printf 'ID uniqueness check failed: %s\n' "$label" >&2
        return 1
    fi
}

run_ab() {
    local label="$1"
    local url="$2"
    local request_count="$3"
    local concurrency="$4"
    local output_file="$TEST_DIR/$label-ab.log"
    local failed
    local non_2xx
    local rps

    ab -l -n "$request_count" -c "$concurrency" -k "$url" > "$output_file" 2>&1
    failed="$(awk '/Failed requests:/ { print $3 }' "$output_file")"
    non_2xx="$(awk '/Non-2xx responses:/ { print $3 }' "$output_file")"
    rps="$(awk '/Requests per second:/ { print $4 }' "$output_file")"
    printf '%s: failed=%s non_2xx=%s requests_per_second=%s\n' \
        "$label" "${failed:-0}" "${non_2xx:-0}" "${rps:-unknown}"

    if [[ "${failed:-0}" -ne 0 || "${non_2xx:-0}" -ne 0 ]]; then
        printf 'HTTP load check failed: %s\n' "$label" >&2
        return 1
    fi
}

printf 'Preparing current build and Docker MySQL...\n'
"${COMPOSE[@]}" up -d --wait mysql
(cd "$REPO_DIR" && mvn -q -pl leaf-server -am package -DskipTests)

if [[ ! -f "$JAR_FILE" ]]; then
    printf 'Missing packaged server: %s\n' "$JAR_FILE" >&2
    exit 1
fi

start_server "$SINGLE_PORT"
SINGLE_URL="http://127.0.0.1:$SINGLE_PORT$ENDPOINT_PATH"

printf '\n[1/5] Concurrent ID uniqueness\n'
request_ids "$SINGLE_URL" "$UNIQUE_REQUESTS" "$UNIQUE_CONCURRENCY" "$TEST_DIR/single-ids.txt"
assert_unique_ids single-instance "$TEST_DIR/single-ids.txt" "$UNIQUE_REQUESTS"

printf '\n[2/5] Service restart recovery\n'
request_ids "$SINGLE_URL" "$RESTART_REQUESTS" "$UNIQUE_CONCURRENCY" "$TEST_DIR/before-restart.txt"
stop_server "$LAST_PID"
start_server "$SINGLE_PORT"
request_ids "$SINGLE_URL" "$RESTART_REQUESTS" "$UNIQUE_CONCURRENCY" "$TEST_DIR/after-restart.txt"
cat "$TEST_DIR/before-restart.txt" "$TEST_DIR/after-restart.txt" > "$TEST_DIR/restart-ids.txt"
assert_unique_ids restart-recovery "$TEST_DIR/restart-ids.txt" "$((RESTART_REQUESTS * 2))"

printf '\n[3/5] MySQL stop/start recovery\n'
"${COMPOSE[@]}" stop mysql
sleep 3
"${COMPOSE[@]}" up -d --wait mysql
request_ids "$SINGLE_URL" "$RECOVERY_REQUESTS" "$UNIQUE_CONCURRENCY" "$TEST_DIR/recovery-ids.txt"
assert_unique_ids mysql-recovery "$TEST_DIR/recovery-ids.txt" "$RECOVERY_REQUESTS"

printf '\n[4/5] Sustained load (%ss)\n' "$LONG_SECONDS"
start_time="$(date +%s)"
round=0
while [[ $(( $(date +%s) - start_time )) -lt "$LONG_SECONDS" ]]; do
    round=$((round + 1))
    run_ab "long-$round" "$SINGLE_URL" "$LONG_BATCH_REQUESTS" "$LONG_CONCURRENCY"
done
printf 'Sustained load rounds=%s\n' "$round"

printf '\n[5/5] Two-instance database competition\n'
stop_server "$LAST_PID"
start_server "$SINGLE_PORT"
FIRST_PID="$LAST_PID"
start_server "$SECOND_PORT"
SECOND_URL="http://127.0.0.1:$SECOND_PORT$ENDPOINT_PATH"
request_ids "$SINGLE_URL" "$MULTI_INSTANCE_REQUESTS" "$MULTI_INSTANCE_CONCURRENCY" "$TEST_DIR/instance-one.txt" &
FIRST_REQUEST_PID=$!
request_ids "$SECOND_URL" "$MULTI_INSTANCE_REQUESTS" "$MULTI_INSTANCE_CONCURRENCY" "$TEST_DIR/instance-two.txt" &
SECOND_REQUEST_PID=$!
wait "$FIRST_REQUEST_PID"
wait "$SECOND_REQUEST_PID"
cat "$TEST_DIR/instance-one.txt" "$TEST_DIR/instance-two.txt" > "$TEST_DIR/multi-instance-ids.txt"
assert_unique_ids two-instances "$TEST_DIR/multi-instance-ids.txt" "$((MULTI_INSTANCE_REQUESTS * 2))"

printf '\nAll segment-mode checks passed.\n'
