#!/bin/bash
# Simple stress test for Agent Product
# Usage: ./stress-test.sh [concurrent_users] [total_requests]
#
# This script provides a quick way to test the agent without k6.
# It uses curl and GNU parallel for concurrent requests.

set -e

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8123}"
API_PREFIX="/api"
CONCURRENT_USERS="${1:-5}"
TOTAL_REQUESTS="${2:-20}"
RESULTS_DIR="./stress-test-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Agent Product Stress Test"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Concurrent Users: $CONCURRENT_USERS"
echo "Total Requests: $TOTAL_REQUESTS"
echo "Results Directory: $RESULTS_DIR"
echo "=========================================="

# Create results directory
mkdir -p "$RESULTS_DIR"

# Test messages
MESSAGES=(
    "你好，我想了解一下职业规划"
    "帮我分析一下我的技能优势"
    "推荐一些学习资源"
    "如何准备技术面试？"
    "我的职业发展方向是什么？"
)

# Function: Get random message
get_random_message() {
    local index=$((RANDOM % ${#MESSAGES[@]}))
    echo "${MESSAGES[$index]}"
}

# Function: Health check
health_check() {
    echo -e "${YELLOW}Running health check...${NC}"

    local response
    response=$(curl -s -w "\n%{http_code}" "$BASE_URL${API_PREFIX}/actuator/health" 2>/dev/null)

    local http_code
    http_code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" -eq 200 ]; then
        local status
        status=$(echo "$body" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        if [ "$status" = "UP" ]; then
            echo -e "${GREEN}✓ Health check passed${NC}"
            return 0
        fi
    fi

    echo -e "${RED}✗ Health check failed (HTTP $http_code)${NC}"
    return 1
}

# Function: Get metrics
get_metrics() {
    echo -e "${YELLOW}Fetching metrics...${NC}"

    local response
    response=$(curl -s "$BASE_URL${API_PREFIX}/actuator/agent-metrics" 2>/dev/null)

    if [ $? -eq 0 ]; then
        echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
    else
        echo -e "${RED}Failed to fetch metrics${NC}"
    fi
}

# Function: Single chat request
chat_request() {
    local user_id="test_user_$$"
    local message
    message=$(get_random_message)

    local payload
    payload=$(cat <<EOF
{
    "message": "$message",
    "userId": "$user_id"
}
EOF
    )

    local start_time
    start_time=$(date +%s%N)

    local response
    response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
        -X POST "$BASE_URL${API_PREFIX}/ai/chat" \
        -H "Content-Type: application/json" \
        -d "$payload" \
        --max-time 30 \
        2>/dev/null)

    local end_time
    end_time=$(date +%s%N)

    local http_code
    http_code=$(echo "$response" | tail -n2 | head -n1)
    local time_total
    time_total=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | head -n-2)

    local duration_ms=$(( (end_time - start_time) / 1000000 ))

    # Output result
    echo "$http_code $duration_ms $user_id"

    return 0
}

# Function: Run concurrent requests
run_concurrent_test() {
    echo -e "${YELLOW}Running concurrent test...${NC}"
    echo "Sending $TOTAL_REQUESTS requests with $CONCURRENT_USERS concurrent users"

    local results_file="$RESULTS_DIR/results_${TIMESTAMP}.txt"
    local start_time
    start_time=$(date +%s)

    # Run requests in parallel
    for i in $(seq 1 "$TOTAL_REQUESTS"); do
        chat_request >> "$results_file" &
        # Control concurrency
        if (( i % CONCURRENT_USERS == 0 )); then
            wait
        fi
    done
    wait

    local end_time
    end_time=$(date +%s)
    local total_duration=$((end_time - start_time))

    # Analyze results
    echo ""
    echo "=========================================="
    echo "Test Results"
    echo "=========================================="

    local total_requests
    total_requests=$(wc -l < "$results_file")
    local success_count
    success_count=$(awk '$1 == 200' "$results_file" | wc -l)
    local failed_count=$((total_requests - success_count))

    # Calculate percentiles
    local sorted_durations
    sorted_durations=$(awk '{print $2}' "$results_file" | sort -n)
    local p50_idx=$((total_requests * 50 / 100))
    local p95_idx=$((total_requests * 95 / 100))
    local p99_idx=$((total_requests * 99 / 100))

    local p50
    p50=$(echo "$sorted_durations" | sed -n "${p50_idx}p")
    local p95
    p95=$(echo "$sorted_durations" | sed -n "${p95_idx}p")
    local p99
    p99=$(echo "$sorted_durations" | sed -n "${p99_idx}p")

    # Calculate average
    local avg_duration
    avg_duration=$(awk '{sum+=$2} END {print sum/NR}' "$results_file")

    echo "Total Requests: $total_requests"
    echo "Successful: $success_count"
    echo "Failed: $failed_count"
    echo "Total Duration: ${total_duration}s"
    echo "Requests/sec: $(echo "scale=2; $total_requests / $total_duration" | bc)"
    echo ""
    echo "Latency Percentiles:"
    echo "  P50: ${p50}ms"
    echo "  P95: ${p95}ms"
    echo "  P99: ${p99}ms"
    echo "  Avg: $(printf "%.2f" "$avg_duration")ms"
    echo ""

    # Save summary
    cat > "$RESULTS_DIR/summary_${TIMESTAMP}.txt" <<EOF
Stress Test Summary
===================
Timestamp: $(date)
Base URL: $BASE_URL
Concurrent Users: $CONCURRENT_USERS
Total Requests: $TOTAL_REQUESTS

Results:
- Total Requests: $total_requests
- Successful: $success_count
- Failed: $failed_count
- Total Duration: ${total_duration}s
- Requests/sec: $(echo "scale=2; $total_requests / $total_duration" | bc)

Latency:
- P50: ${p50}ms
- P95: ${p95}ms
- P99: ${p99}ms
- Avg: $(printf "%.2f" "$avg_duration")ms

Raw data: $results_file
EOF

    echo -e "${GREEN}Results saved to $RESULTS_DIR/summary_${TIMESTAMP}.txt${NC}"
}

# ─── Main Flow ────────────────────────────────────────────────────────

# Health check
if ! health_check; then
    echo -e "${RED}Server is not healthy. Aborting test.${NC}"
    exit 1
fi

# Get initial metrics
echo ""
echo "Initial metrics:"
get_metrics

# Run test
echo ""
run_concurrent_test

# Get final metrics
echo ""
echo "Final metrics:"
get_metrics

echo ""
echo -e "${GREEN}Stress test completed!${NC}"
