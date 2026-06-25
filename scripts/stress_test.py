#!/usr/bin/env python3
"""
Agent Product SSE Stress Test
==============================
Stress test for SSE streaming endpoints with detailed metrics.

Usage:
    python stress_test.py                          # Default: 5 concurrent, 20 total
    python stress_test.py -c 10 -n 50             # 10 concurrent, 50 total
    python stress_test.py -c 20 -n 100 --token YOUR_JWT

Metrics collected:
    - Time to First Token (TTFT)
    - Total response time
    - P50 / P95 / P99 latency
    - QualityGuard LLM call count (from SSE events)
    - Error rate
    - Throughput (requests/sec)

@author agent_product team
"""

import argparse
import json
import statistics
import sys
import time
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Optional

try:
    import requests
except ImportError:
    print("ERROR: 'requests' not installed. Run: pip install requests")
    sys.exit(1)


# ─── Configuration ───────────────────────────────────────────

DEFAULT_URL = "http://localhost:8123/api/ai/chat/stream"
DEFAULT_CONCURRENCY = 5
DEFAULT_TOTAL = 20
DEFAULT_TIMEOUT = 120  # seconds per request

SAMPLE_MESSAGES = [
    "帮我优化一下简历",
    "最近薪资谈判有什么技巧？",
    "我想离职，需要注意什么？",
    "帮我分析一下职业规划",
    "面试的时候怎么自我介绍？",
    "如何提升职场竞争力？",
    "帮我看看这个offer怎么样",
    "工作压力太大怎么办？",
]


# ─── Data Models ─────────────────────────────────────────────

@dataclass
class RequestResult:
    """Result of a single SSE request."""
    request_id: int
    success: bool
    ttft_ms: float = 0.0          # Time to first token (ms)
    total_ms: float = 0.0         # Total time (ms)
    token_count: int = 0          # Number of tokens received
    quality_review_count: int = 0 # QualityGuard SSE events
    quality_blocked: bool = False # Was answer blocked by QualityGuard
    routing_target: str = ""      # Which agent was routed to
    error: Optional[str] = None


@dataclass
class StressTestReport:
    """Aggregated stress test report."""
    total_requests: int = 0
    successful: int = 0
    failed: int = 0
    ttft_values: list = field(default_factory=list)
    total_values: list = field(default_factory=list)
    token_counts: list = field(default_factory=list)
    quality_reviews: int = 0
    quality_blocked: int = 0
    routing_stats: dict = field(default_factory=dict)
    errors: list = field(default_factory=list)
    wall_time_s: float = 0.0


# ─── SSE Request Handler ─────────────────────────────────────

def execute_sse_request(
    request_id: int,
    url: str,
    message: str,
    token: Optional[str],
    timeout: int,
) -> RequestResult:
    """
    Execute a single SSE streaming request and collect metrics.
    """
    result = RequestResult(request_id=request_id, success=False)

    headers = {
        "Accept": "text/event-stream",
        "Cache-Control": "no-cache",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    # Build request body (adjust based on your API)
    payload = {
        "message": message,
        "chatId": f"stress-test-{request_id}",
    }

    start_time = time.monotonic()

    try:
        response = requests.post(
            url,
            json=payload,
            headers=headers,
            stream=True,
            timeout=timeout,
        )
        response.raise_for_status()

        first_token_time = None
        token_count = 0
        quality_reviews = 0
        quality_blocked = False
        routing_target = ""

        for line in response.iter_lines(decode_unicode=True):
            if not line:
                continue

            # Parse SSE format: "event:xxx\ndata:yyy" or just "data:yyy"
            if line.startswith("data:"):
                data = line[5:].strip()

                if first_token_time is None and data and data != "[DONE]":
                    first_token_time = time.monotonic()

                if data == "[DONE]":
                    break

                # Count tokens (each data line with content = 1 token chunk)
                if data and not data.startswith("["):
                    token_count += 1

                # Check for quality events
                if data.startswith("{"):
                    try:
                        event_data = json.loads(data)
                        if "overallScore" in event_data:
                            quality_reviews += 1
                        if "riskLevel" in event_data and event_data.get("riskLevel") == "CRITICAL":
                            quality_blocked = True
                    except json.JSONDecodeError:
                        pass

            elif line.startswith("event:"):
                event_type = line[6:].strip()
                if event_type == "quality-review":
                    quality_reviews += 1
                elif event_type == "quality-blocked":
                    quality_blocked = True
                elif event_type == "routing":
                    # Next data line will have routing info
                    pass

        end_time = time.monotonic()

        result.success = True
        result.total_ms = (end_time - start_time) * 1000
        result.ttft_ms = ((first_token_time - start_time) * 1000) if first_token_time else 0
        result.token_count = token_count
        result.quality_review_count = quality_reviews
        result.quality_blocked = quality_blocked
        result.routing_target = routing_target

    except requests.exceptions.Timeout:
        result.error = f"Timeout after {timeout}s"
        result.total_ms = timeout * 1000
    except requests.exceptions.ConnectionError as e:
        result.error = f"Connection error: {e}"
    except Exception as e:
        result.error = str(e)
        result.total_ms = (time.monotonic() - start_time) * 1000

    return result


# ─── Stress Test Runner ──────────────────────────────────────

def run_stress_test(
    url: str,
    concurrency: int,
    total: int,
    token: Optional[str],
    timeout: int,
) -> StressTestReport:
    """
    Run stress test with given concurrency and total requests.
    """
    report = StressTestReport()
    report.total_requests = total

    print(f"\n{'='*60}")
    print(f"  Agent Product Stress Test")
    print(f"{'='*60}")
    print(f"  URL:         {url}")
    print(f"  Concurrency: {concurrency}")
    print(f"  Total:       {total}")
    print(f"  Timeout:     {timeout}s")
    print(f"  Auth:        {'JWT set' if token else 'No token'}")
    print(f"{'='*60}\n")

    wall_start = time.monotonic()

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = []
        for i in range(total):
            message = SAMPLE_MESSAGES[i % len(SAMPLE_MESSAGES)]
            future = executor.submit(
                execute_sse_request,
                request_id=i + 1,
                url=url,
                message=message,
                token=token,
                timeout=timeout,
            )
            futures.append(future)

        # Collect results with progress
        completed = 0
        for future in as_completed(futures):
            result = future.result()
            completed += 1

            # Progress indicator
            status = "✓" if result.success else "✗"
            ttft = f"{result.ttft_ms:.0f}ms" if result.ttft_ms > 0 else "N/A"
            total_t = f"{result.total_ms:.0f}ms"
            print(f"  [{completed:3d}/{total}] {status}  TTFT={ttft:>8s}  Total={total_t:>8s}  Tokens={result.token_count:3d}"
                  + (f"  QR={result.quality_review_count}" if result.quality_review_count else "")
                  + (f"  ERROR: {result.error}" if result.error else ""))

            # Aggregate
            if result.success:
                report.successful += 1
                report.ttft_values.append(result.ttft_ms)
                report.total_values.append(result.total_ms)
                report.token_counts.append(result.token_count)
                report.quality_reviews += result.quality_review_count
                if result.quality_blocked:
                    report.quality_blocked += 1
            else:
                report.failed += 1
                report.errors.append(result.error)

    report.wall_time_s = time.monotonic() - wall_start
    return report


# ─── Report Formatter ────────────────────────────────────────

def format_percentile(values: list, percentile: float) -> str:
    """Calculate and format a percentile value."""
    if not values:
        return "N/A"
    sorted_vals = sorted(values)
    idx = int(len(sorted_vals) * percentile / 100)
    idx = min(idx, len(sorted_vals) - 1)
    return f"{sorted_vals[idx]:.0f}ms"


def print_report(report: StressTestReport):
    """Print formatted stress test report."""
    print(f"\n{'='*60}")
    print(f"  STRESS TEST REPORT")
    print(f"{'='*60}")

    print(f"\n  ── Summary ──")
    print(f"  Total Requests:   {report.total_requests}")
    print(f"  Successful:       {report.successful}")
    print(f"  Failed:           {report.failed}")
    print(f"  Error Rate:       {report.failed / report.total_requests * 100:.1f}%")
    print(f"  Wall Time:        {report.wall_time_s:.1f}s")
    print(f"  Throughput:       {report.successful / report.wall_time_s:.2f} req/s")

    if report.ttft_values:
        print(f"\n  ── Time to First Token (TTFT) ──")
        print(f"  Min:    {min(report.ttft_values):.0f}ms")
        print(f"  P50:    {format_percentile(report.ttft_values, 50)}")
        print(f"  P95:    {format_percentile(report.ttft_values, 95)}")
        print(f"  P99:    {format_percentile(report.ttft_values, 99)}")
        print(f"  Max:    {max(report.ttft_values):.0f}ms")
        print(f"  Mean:   {statistics.mean(report.ttft_values):.0f}ms")

    if report.total_values:
        print(f"\n  ── Total Response Time ──")
        print(f"  Min:    {min(report.total_values):.0f}ms")
        print(f"  P50:    {format_percentile(report.total_values, 50)}")
        print(f"  P95:    {format_percentile(report.total_values, 95)}")
        print(f"  P99:    {format_percentile(report.total_values, 99)}")
        print(f"  Max:    {max(report.total_values):.0f}ms")
        print(f"  Mean:   {statistics.mean(report.total_values):.0f}ms")

    if report.token_counts:
        print(f"\n  ── Token Stats ──")
        print(f"  Total Tokens:     {sum(report.token_counts)}")
        print(f"  Avg per Request:  {statistics.mean(report.token_counts):.1f}")
        print(f"  Tokens/sec:       {sum(report.token_counts) / report.wall_time_s:.1f}")

    print(f"\n  ── QualityGuard ──")
    print(f"  Reviews Triggered: {report.quality_reviews}")
    print(f"  Answers Blocked:   {report.quality_blocked}")

    if report.errors:
        print(f"\n  ── Errors ──")
        error_counts = {}
        for err in report.errors:
            err_short = str(err)[:80]
            error_counts[err_short] = error_counts.get(err_short, 0) + 1
        for err, count in error_counts.items():
            print(f"  [{count}x] {err}")

    print(f"\n{'='*60}\n")

    # Save report to JSON
    report_data = {
        "total_requests": report.total_requests,
        "successful": report.successful,
        "failed": report.failed,
        "wall_time_s": round(report.wall_time_s, 2),
        "throughput_rps": round(report.successful / report.wall_time_s, 2),
        "ttft_p50": format_percentile(report.ttft_values, 50),
        "ttft_p95": format_percentile(report.ttft_values, 95),
        "total_p50": format_percentile(report.total_values, 50),
        "total_p95": format_percentile(report.total_values, 95),
        "quality_reviews": report.quality_reviews,
        "quality_blocked": report.quality_blocked,
    }
    report_file = f"stress_test_report_{int(time.time())}.json"
    with open(report_file, "w") as f:
        json.dump(report_data, f, indent=2)
    print(f"  Report saved to: {report_file}")


# ─── Main ────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Agent Product SSE Stress Test")
    parser.add_argument("-u", "--url", default=DEFAULT_URL, help="SSE endpoint URL")
    parser.add_argument("-c", "--concurrency", type=int, default=DEFAULT_CONCURRENCY, help="Concurrent requests")
    parser.add_argument("-n", "--total", type=int, default=DEFAULT_TOTAL, help="Total requests")
    parser.add_argument("-t", "--token", default=None, help="JWT token for auth")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT, help="Per-request timeout (seconds)")

    args = parser.parse_args()

    report = run_stress_test(
        url=args.url,
        concurrency=args.concurrency,
        total=args.total,
        token=args.token,
        timeout=args.timeout,
    )

    print_report(report)


if __name__ == "__main__":
    main()
