#!/usr/bin/env bash
# =============================================================================
# CodeRank Integration Test Suite — cURL Executable Script
# =============================================================================
# Covers:
#   Path 1: Admin Setup + Interpreted Execution (Python — Two Sum)
#   Path 2: Compiled Execution Flow (Java + C++ — Factorial)
#   Path 3: Aggregated Test Matrix Flow (JavaScript — FizzBuzz, 5 test cases)
#   Negative: RBAC enforcement checks
#
# Usage:
#   chmod +x run_integration_tests.sh
#   ./run_integration_tests.sh
#
# Requirements:
#   - curl, jq installed
#   - CodeRank stack running on localhost:8080
# =============================================================================

set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="admin@coderank.io"
ADMIN_PASSWORD="Admin@1234"
USER_EMAIL="testuser_curl_$(date +%s)@coderank.io"
USER_PASSWORD="TestUser@1234"
USER_USERNAME="testuser_curl_$(date +%s)"
POLL_INTERVAL=3
MAX_POLL_ATTEMPTS=20

# ─── Colour helpers ───────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "${GREEN}  ✔ PASS${NC} — $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}  ✘ FAIL${NC} — $1"; FAIL=$((FAIL+1)); }
info() { echo -e "${CYAN}  ℹ${NC}  $1"; }
section() { echo -e "\n${BLUE}══════════════════════════════════════════════════════${NC}"; echo -e "${BLUE}  $1${NC}"; echo -e "${BLUE}══════════════════════════════════════════════════════${NC}"; }
step() { echo -e "\n${YELLOW}  ▶ $1${NC}"; }

assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    pass "$label (expected: '$expected', got: '$actual')"
  else
    fail "$label (expected: '$expected', got: '$actual')"
  fi
}

assert_not_empty() {
  local label="$1" value="$2"
  if [ -n "$value" ] && [ "$value" != "null" ]; then
    pass "$label (value: '${value:0:40}...')"
  else
    fail "$label — value is empty or null"
  fi
}

assert_http() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    pass "HTTP $label — status $actual"
  else
    fail "HTTP $label — expected $expected, got $actual"
  fi
}

# ─── Poll until terminal state ─────────────────────────────────────────────────
poll_verdict() {
  local submission_id="$1"
  local jwt="$2"
  local label="$3"
  local attempt=0

  info "Polling $label — submissionId: $submission_id"
  while [ $attempt -lt $MAX_POLL_ATTEMPTS ]; do
    attempt=$((attempt+1))
    local response http_status
    response=$(curl -s -w "\n%{http_code}" \
      -H "Authorization: Bearer $jwt" \
      "$BASE_URL/api/v1/submissions/$submission_id/result")
    http_status=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | head -n -1)

    local status verdict
    status=$(echo "$body" | jq -r '.status // "null"')
    verdict=$(echo "$body" | jq -r '.verdict // "null"')

    info "  Attempt $attempt/$MAX_POLL_ATTEMPTS — status: $status, verdict: $verdict"

    if [[ "$status" == "COMPLETED" || "$status" == "FAILED" || "$status" == "TIMEDOUT" ]]; then
      echo "$body"
      return 0
    fi
    sleep $POLL_INTERVAL
  done

  fail "$label — polling timed out after $((MAX_POLL_ATTEMPTS * POLL_INTERVAL))s"
  echo "{}"
  return 1
}

# =============================================================================
# PATH 1: Admin Setup + Interpreted Execution (Python)
# =============================================================================
section "PATH 1 — Admin Setup + Interpreted Execution (Python)"

# ── P1-S1: Admin Login ────────────────────────────────────────────────────────
step "P1-S1: Admin Login"
ADMIN_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
ADMIN_HTTP=$(echo "$ADMIN_RESPONSE" | tail -n1)
ADMIN_BODY=$(echo "$ADMIN_RESPONSE" | head -n -1)

assert_http "Admin Login" "200" "$ADMIN_HTTP"
ADMIN_JWT=$(echo "$ADMIN_BODY" | jq -r '.accessToken // ""')
ADMIN_ROLE=$(echo "$ADMIN_BODY" | jq -r '.role // ""')
assert_not_empty "Admin JWT extracted" "$ADMIN_JWT"
assert_eq "Admin role is ROLE_ADMIN" "ROLE_ADMIN" "$ADMIN_ROLE"
info "Admin JWT: ${ADMIN_JWT:0:60}..."

# ── P1-S2: Admin Creates Problem ──────────────────────────────────────────────
step "P1-S2: Admin Creates Problem (Two Sum — EASY)"
CREATE_P1_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/problems" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -d '{
    "title": "Two Sum",
    "description": "Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target.",
    "difficulty": "EASY",
    "constraints": "2 <= nums.length <= 10^4\n-10^9 <= nums[i] <= 10^9",
    "examples": [
      {
        "inputText": "nums = [2,7,11,15], target = 9",
        "outputText": "[0,1]",
        "explanation": "nums[0] + nums[1] = 9",
        "orderIndex": 1
      }
    ],
    "testCases": [
      {
        "input": "2\n2 7 11 15\n9",
        "expected": "0 1",
        "isSample": true,
        "orderIndex": 1
      }
    ]
  }')
CREATE_P1_HTTP=$(echo "$CREATE_P1_RESPONSE" | tail -n1)
CREATE_P1_BODY=$(echo "$CREATE_P1_RESPONSE" | head -n -1)

assert_http "Admin Create Problem" "201" "$CREATE_P1_HTTP"
PROBLEM_ID_P1=$(echo "$CREATE_P1_BODY" | jq -r '.id // ""')
PROBLEM_SLUG_P1=$(echo "$CREATE_P1_BODY" | jq -r '.slug // ""')
P1_TITLE=$(echo "$CREATE_P1_BODY" | jq -r '.title // ""')
P1_DIFF=$(echo "$CREATE_P1_BODY" | jq -r '.difficulty // ""')
assert_not_empty "Problem ID extracted" "$PROBLEM_ID_P1"
assert_not_empty "Problem slug extracted" "$PROBLEM_SLUG_P1"
assert_eq "Problem title is Two Sum" "Two Sum" "$P1_TITLE"
assert_eq "Problem difficulty is EASY" "EASY" "$P1_DIFF"
info "Problem ID (P1): $PROBLEM_ID_P1"
info "Problem Slug (P1): $PROBLEM_SLUG_P1"

# ── P1-S3: Register Standard User ─────────────────────────────────────────────
step "P1-S3: Register Standard User"
REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$USER_USERNAME\",
    \"email\": \"$USER_EMAIL\",
    \"password\": \"$USER_PASSWORD\"
  }")
REGISTER_HTTP=$(echo "$REGISTER_RESPONSE" | tail -n1)
REGISTER_BODY=$(echo "$REGISTER_RESPONSE" | head -n -1)

assert_http "User Registration" "201" "$REGISTER_HTTP"
USER_JWT=$(echo "$REGISTER_BODY" | jq -r '.accessToken // ""')
USER_ROLE=$(echo "$REGISTER_BODY" | jq -r '.role // ""')
assert_not_empty "User JWT extracted" "$USER_JWT"
assert_eq "User role is ROLE_USER" "ROLE_USER" "$USER_ROLE"
info "User JWT: ${USER_JWT:0:60}..."

# ── P1-S4: User GETs Problem by Slug ──────────────────────────────────────────
step "P1-S4: User GETs Problem by Slug"
GET_PROBLEM_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: Bearer $USER_JWT" \
  "$BASE_URL/api/v1/problems/$PROBLEM_SLUG_P1")
GET_PROBLEM_HTTP=$(echo "$GET_PROBLEM_RESPONSE" | tail -n1)
GET_PROBLEM_BODY=$(echo "$GET_PROBLEM_RESPONSE" | head -n -1)

assert_http "User GET Problem by Slug" "200" "$GET_PROBLEM_HTTP"
RETURNED_ID=$(echo "$GET_PROBLEM_BODY" | jq -r '.id // ""')
RETURNED_TITLE=$(echo "$GET_PROBLEM_BODY" | jq -r '.title // ""')
TEST_CASES_FIELD=$(echo "$GET_PROBLEM_BODY" | jq -r '.testCases // "absent"')
assert_eq "Problem ID matches" "$PROBLEM_ID_P1" "$RETURNED_ID"
assert_eq "Problem title matches" "Two Sum" "$RETURNED_TITLE"
assert_eq "Test cases NOT exposed to user" "absent" "$TEST_CASES_FIELD"

# ── P1-S5: User Submits Python Code ───────────────────────────────────────────
step "P1-S5: User Submits Python Code (Two Sum)"
PYTHON_CODE='import sys
input_data = sys.stdin.read().split()
n = int(input_data[0])
nums = list(map(int, input_data[1:n+1]))
target = int(input_data[n+1])
lookup = {}
for i, num in enumerate(nums):
    complement = target - num
    if complement in lookup:
        print(lookup[complement], i)
        break
    lookup[num] = i
'
SUBMIT_P1_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/problems/$PROBLEM_ID_P1/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_JWT" \
  -d "{
    \"language\": \"python\",
    \"sourceCode\": $(echo "$PYTHON_CODE" | jq -Rs .)
  }")
SUBMIT_P1_HTTP=$(echo "$SUBMIT_P1_RESPONSE" | tail -n1)
SUBMIT_P1_BODY=$(echo "$SUBMIT_P1_RESPONSE" | head -n -1)

assert_http "Python Submit" "202" "$SUBMIT_P1_HTTP"
SUBMISSION_ID_P1=$(echo "$SUBMIT_P1_BODY" | jq -r '.submissionId // ""')
SUBMIT_P1_LANG=$(echo "$SUBMIT_P1_BODY" | jq -r '.language // ""')
assert_not_empty "Submission ID extracted" "$SUBMISSION_ID_P1"
assert_eq "Language is python" "python" "$SUBMIT_P1_LANG"
info "Submission ID (P1): $SUBMISSION_ID_P1"

# ── P1-S6: Poll for Python Verdict ────────────────────────────────────────────
step "P1-S6: Poll for Python Verdict"
POLL_P1_BODY=$(poll_verdict "$SUBMISSION_ID_P1" "$USER_JWT" "Path 1 — Python")
P1_STATUS=$(echo "$POLL_P1_BODY" | jq -r '.status // "null"')
P1_VERDICT=$(echo "$POLL_P1_BODY" | jq -r '.verdict // "null"')
P1_EXEC_MS=$(echo "$POLL_P1_BODY" | jq -r '.executionTimeMs // "null"')
P1_SOURCE=$(echo "$POLL_P1_BODY" | jq -r '.source // "null"')
assert_eq "Python execution status is COMPLETED" "COMPLETED" "$P1_STATUS"
assert_eq "Python verdict is ACCEPTED" "ACCEPTED" "$P1_VERDICT"
assert_not_empty "executionTimeMs is populated" "$P1_EXEC_MS"
assert_not_empty "source field is present" "$P1_SOURCE"

# =============================================================================
# PATH 2: Compiled Execution Flow (Java + C++)
# =============================================================================
section "PATH 2 — Compiled Execution Flow (Java + C++)"

# ── P2-S1: Admin Creates Problem for Java ─────────────────────────────────────
step "P2-S1: Admin Creates Problem for Java (Factorial — MEDIUM)"
CREATE_P2_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/problems" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -d '{
    "title": "Factorial",
    "description": "Given a non-negative integer n, compute and return n!. Read n from stdin and print the result to stdout.",
    "difficulty": "MEDIUM",
    "constraints": "0 <= n <= 12",
    "examples": [
      {
        "inputText": "5",
        "outputText": "120",
        "explanation": "5! = 120",
        "orderIndex": 1
      }
    ],
    "testCases": [
      {
        "input": "5",
        "expected": "120",
        "isSample": true,
        "orderIndex": 1
      }
    ]
  }')
CREATE_P2_HTTP=$(echo "$CREATE_P2_RESPONSE" | tail -n1)
CREATE_P2_BODY=$(echo "$CREATE_P2_RESPONSE" | head -n -1)

assert_http "Admin Create Java Problem" "201" "$CREATE_P2_HTTP"
PROBLEM_ID_P2=$(echo "$CREATE_P2_BODY" | jq -r '.id // ""')
P2_DIFF=$(echo "$CREATE_P2_BODY" | jq -r '.difficulty // ""')
assert_not_empty "Problem ID (P2) extracted" "$PROBLEM_ID_P2"
assert_eq "Problem difficulty is MEDIUM" "MEDIUM" "$P2_DIFF"
info "Problem ID (P2): $PROBLEM_ID_P2"

# ── P2-S2: User Submits Java Code ─────────────────────────────────────────────
step "P2-S2: User Submits Java Code (Factorial)"
JAVA_CODE='import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        System.out.println(result);
    }
}
'
SUBMIT_P2_JAVA_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/problems/$PROBLEM_ID_P2/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_JWT" \
  -d "{
    \"language\": \"java\",
    \"sourceCode\": $(echo "$JAVA_CODE" | jq -Rs .)
  }")
SUBMIT_P2_JAVA_HTTP=$(echo "$SUBMIT_P2_JAVA_RESPONSE" | tail -n1)
SUBMIT_P2_JAVA_BODY=$(echo "$SUBMIT_P2_JAVA_RESPONSE" | head -n -1)

assert_http "Java Submit" "202" "$SUBMIT_P2_JAVA_HTTP"
SUBMISSION_ID_P2_JAVA=$(echo "$SUBMIT_P2_JAVA_BODY" | jq -r '.submissionId // ""')
SUBMIT_P2_LANG=$(echo "$SUBMIT_P2_JAVA_BODY" | jq -r '.language // ""')
assert_not_empty "Java Submission ID extracted" "$SUBMISSION_ID_P2_JAVA"
assert_eq "Language is java" "java" "$SUBMIT_P2_LANG"
info "Java Submission ID: $SUBMISSION_ID_P2_JAVA"

# ── P2-S3: Poll for Java Verdict ──────────────────────────────────────────────
step "P2-S3: Poll for Java Verdict"
POLL_P2_JAVA_BODY=$(poll_verdict "$SUBMISSION_ID_P2_JAVA" "$USER_JWT" "Path 2 — Java")
P2_JAVA_STATUS=$(echo "$POLL_P2_JAVA_BODY" | jq -r '.status // "null"')
P2_JAVA_VERDICT=$(echo "$POLL_P2_JAVA_BODY" | jq -r '.verdict // "null"')
P2_JAVA_EXEC_MS=$(echo "$POLL_P2_JAVA_BODY" | jq -r '.executionTimeMs // "null"')
assert_eq "Java execution status is COMPLETED" "COMPLETED" "$P2_JAVA_STATUS"
assert_eq "Java verdict is ACCEPTED — confirms javac compilation + JVM execution" "ACCEPTED" "$P2_JAVA_VERDICT"
assert_not_empty "Java executionTimeMs is populated" "$P2_JAVA_EXEC_MS"

# ── P2-S4: User Submits C++ Code ──────────────────────────────────────────────
step "P2-S4: User Submits C++ Code (Factorial)"
CPP_CODE='#include <iostream>
using namespace std;
int main() {
    long long n;
    cin >> n;
    long long result = 1;
    for (long long i = 2; i <= n; i++) {
        result *= i;
    }
    cout << result << endl;
    return 0;
}
'
SUBMIT_P2_CPP_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/problems/$PROBLEM_ID_P2/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_JWT" \
  -d "{
    \"language\": \"cpp\",
    \"sourceCode\": $(echo "$CPP_CODE" | jq -Rs .)
  }")
SUBMIT_P2_CPP_HTTP=$(echo "$SUBMIT_P2_CPP_RESPONSE" | tail -n1)
SUBMIT_P2_CPP_BODY=$(echo "$SUBMIT_P2_CPP_RESPONSE" | head -n -1)

assert_http "C++ Submit" "202" "$SUBMIT_P2_CPP_HTTP"
SUBMISSION_ID_P2_CPP=$(echo "$SUBMIT_P2_CPP_BODY" | jq -r '.submissionId // ""')
SUBMIT_CPP_LANG=$(echo "$SUBMIT_P2_CPP_BODY" | jq -r '.language // ""')
assert_not_empty "C++ Submission ID extracted" "$SUBMISSION_ID_P2_CPP"
assert_eq "Language is cpp" "cpp" "$SUBMIT_CPP_LANG"
info "C++ Submission ID: $SUBMISSION_ID_P2_CPP"

# ── P2-S5: Poll for C++ Verdict ───────────────────────────────────────────────
step "P2-S5: Poll for C++ Verdict"
POLL_P2_CPP_BODY=$(poll_verdict "$SUBMISSION_ID_P2_CPP" "$USER_JWT" "Path 2 — C++")
P2_CPP_STATUS=$(echo "$POLL_P2_CPP_BODY" | jq -r '.status // "null"')
P2_CPP_VERDICT=$(echo "$POLL_P2_CPP_BODY" | jq -r '.verdict // "null"')
P2_CPP_EXEC_MS=$(echo "$POLL_P2_CPP_BODY" | jq -r '.executionTimeMs // "null"')
assert_eq "C++ execution status is COMPLETED" "COMPLETED" "$P2_CPP_STATUS"
assert_eq "C++ verdict is ACCEPTED — confirms g++ compilation + binary execution" "ACCEPTED" "$P2_CPP_VERDICT"
assert_not_empty "C++ executionTimeMs is populated" "$P2_CPP_EXEC_MS"

# =============================================================================
# PATH 3: Aggregated Test Matrix Flow (5 Test Cases — JavaScript)
# =============================================================================
section "PATH 3 — Aggregated Test Matrix Flow (JavaScript — 5 Test Cases)"

# ── P3-S1: Admin Creates Multi-Test-Case Problem ──────────────────────────────
step "P3-S1: Admin Creates FizzBuzz Matrix Problem (HARD — 5 test cases)"
CREATE_P3_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/problems" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -d '{
    "title": "FizzBuzz Matrix",
    "description": "Given an integer n, print numbers from 1 to n. For multiples of 3 print Fizz, for multiples of 5 print Buzz, for multiples of both print FizzBuzz.",
    "difficulty": "HARD",
    "constraints": "1 <= n <= 100",
    "examples": [
      {
        "inputText": "5",
        "outputText": "1\n2\nFizz\n4\nBuzz",
        "explanation": "3 divisible by 3 = Fizz, 5 divisible by 5 = Buzz",
        "orderIndex": 1
      }
    ],
    "testCases": [
      { "input": "5",  "expected": "1\n2\nFizz\n4\nBuzz", "isSample": true,  "orderIndex": 1 },
      { "input": "15", "expected": "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz", "isSample": false, "orderIndex": 2 },
      { "input": "1",  "expected": "1", "isSample": false, "orderIndex": 3 },
      { "input": "3",  "expected": "1\n2\nFizz", "isSample": false, "orderIndex": 4 },
      { "input": "10", "expected": "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz", "isSample": false, "orderIndex": 5 }
    ]
  }')
CREATE_P3_HTTP=$(echo "$CREATE_P3_RESPONSE" | tail -n1)
CREATE_P3_BODY=$(echo "$CREATE_P3_RESPONSE" | head -n -1)

assert_http "Admin Create Matrix Problem" "201" "$CREATE_P3_HTTP"
PROBLEM_ID_P3=$(echo "$CREATE_P3_BODY" | jq -r '.id // ""')
P3_DIFF=$(echo "$CREATE_P3_BODY" | jq -r '.difficulty // ""')
assert_not_empty "Problem ID (P3) extracted" "$PROBLEM_ID_P3"
assert_eq "Problem difficulty is HARD" "HARD" "$P3_DIFF"
info "Problem ID (P3): $PROBLEM_ID_P3"

# ── P3-S2: User Submits JavaScript Code ───────────────────────────────────────
step "P3-S2: User Submits JavaScript Code (FizzBuzz Matrix)"
JS_CODE="const readline = require('readline');
const rl = readline.createInterface({ input: process.stdin });
rl.on('line', (line) => {
    const n = parseInt(line.trim(), 10);
    for (let i = 1; i <= n; i++) {
        if (i % 15 === 0) process.stdout.write('FizzBuzz\n');
        else if (i % 3 === 0) process.stdout.write('Fizz\n');
        else if (i % 5 === 0) process.stdout.write('Buzz\n');
        else process.stdout.write(i + '\n');
    }
    rl.close();
});
"
SUBMIT_P3_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/problems/$PROBLEM_ID_P3/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_JWT" \
  -d "{
    \"language\": \"javascript\",
    \"sourceCode\": $(echo "$JS_CODE" | jq -Rs .)
  }")
SUBMIT_P3_HTTP=$(echo "$SUBMIT_P3_RESPONSE" | tail -n1)
SUBMIT_P3_BODY=$(echo "$SUBMIT_P3_RESPONSE" | head -n -1)

assert_http "JavaScript Submit (Matrix)" "202" "$SUBMIT_P3_HTTP"
SUBMISSION_ID_P3=$(echo "$SUBMIT_P3_BODY" | jq -r '.submissionId // ""')
SUBMIT_P3_LANG=$(echo "$SUBMIT_P3_BODY" | jq -r '.language // ""')
assert_not_empty "Matrix Submission ID extracted" "$SUBMISSION_ID_P3"
assert_eq "Language is javascript" "javascript" "$SUBMIT_P3_LANG"
info "Matrix Submission ID: $SUBMISSION_ID_P3"

# ── P3-S3: Poll for Matrix Aggregated Verdict ─────────────────────────────────
step "P3-S3: Poll for Aggregated Verdict Across All 5 Test Cases"
POLL_P3_BODY=$(poll_verdict "$SUBMISSION_ID_P3" "$USER_JWT" "Path 3 — JavaScript Matrix")
P3_STATUS=$(echo "$POLL_P3_BODY" | jq -r '.status // "null"')
P3_VERDICT=$(echo "$POLL_P3_BODY" | jq -r '.verdict // "null"')
P3_EXEC_MS=$(echo "$POLL_P3_BODY" | jq -r '.executionTimeMs // "null"')
P3_COMPLETED_AT=$(echo "$POLL_P3_BODY" | jq -r '.completedAt // "null"')
P3_SOURCE=$(echo "$POLL_P3_BODY" | jq -r '.source // "null"')
assert_eq "Matrix execution status is COMPLETED" "COMPLETED" "$P3_STATUS"
assert_eq "Aggregated verdict across all 5 test cases is ACCEPTED" "ACCEPTED" "$P3_VERDICT"
assert_not_empty "executionTimeMs populated — confirms aggregation" "$P3_EXEC_MS"
assert_not_empty "source field confirms cache or db" "$P3_SOURCE"
info "Total execution time: ${P3_EXEC_MS}ms"

# ── P3-S4: Verify Submission Listed in My Submissions ─────────────────────────
step "P3-S4: Verify Submission Appears in My Submissions List"
MY_SUBS_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: Bearer $USER_JWT" \
  "$BASE_URL/api/v1/submissions?problemId=$PROBLEM_ID_P3")
MY_SUBS_HTTP=$(echo "$MY_SUBS_RESPONSE" | tail -n1)
MY_SUBS_BODY=$(echo "$MY_SUBS_RESPONSE" | head -n -1)

assert_http "List My Submissions" "200" "$MY_SUBS_HTTP"
MATCH_COUNT=$(echo "$MY_SUBS_BODY" | jq "[.content[] | select(.problemId == \"$PROBLEM_ID_P3\")] | length")
if [ "$MATCH_COUNT" -ge 1 ]; then
  pass "At least one submission matches Problem ID (P3) in listing"
else
  fail "No submission found in listing matching Problem ID (P3)"
fi

# =============================================================================
# NEGATIVE: RBAC Enforcement Checks
# =============================================================================
section "NEGATIVE — RBAC Enforcement Checks"

# ── NEG-1: Unauthenticated request returns 401 ─────────────────────────────────
step "NEG-1: Unauthenticated Request to Protected Endpoint → 401"
NEG1_RESPONSE=$(curl -s -w "\n%{http_code}" \
  "$BASE_URL/api/v1/problems")
NEG1_HTTP=$(echo "$NEG1_RESPONSE" | tail -n1)
NEG1_BODY=$(echo "$NEG1_RESPONSE" | head -n -1)
assert_http "Unauthenticated GET /problems" "401" "$NEG1_HTTP"
NEG1_ERROR=$(echo "$NEG1_BODY" | jq -r '.error // ""')
assert_eq "Error code is MISSING_TOKEN" "MISSING_TOKEN" "$NEG1_ERROR"

# ── NEG-2: Standard user cannot create problem → 403 ─────────────────────────
step "NEG-2: Standard User Attempts Problem Creation → 403"
NEG2_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/v1/problems" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_JWT" \
  -d '{
    "title": "Unauthorized",
    "description": "Should fail RBAC.",
    "difficulty": "EASY",
    "testCases": [{"input": "1", "expected": "1", "isSample": true, "orderIndex": 1}]
  }')
NEG2_HTTP=$(echo "$NEG2_RESPONSE" | tail -n1)
assert_http "User POST /problems RBAC rejection" "403" "$NEG2_HTTP"

# ── NEG-3: Internal endpoint blocked at Gateway → 403 ────────────────────────
step "NEG-3: Internal Endpoint Blocked at Gateway → 403 (Even with Admin JWT)"
NEG3_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: Bearer $ADMIN_JWT" \
  "$BASE_URL/api/v1/internal/problems/$PROBLEM_ID_P1")
NEG3_HTTP=$(echo "$NEG3_RESPONSE" | tail -n1)
assert_http "Internal endpoint gateway block" "403" "$NEG3_HTTP"

# =============================================================================
# RESULTS SUMMARY
# =============================================================================
section "TEST RESULTS SUMMARY"
TOTAL=$((PASS+FAIL))
echo -e "  Total Tests : $TOTAL"
echo -e "  ${GREEN}Passed      : $PASS${NC}"
echo -e "  ${RED}Failed      : $FAIL${NC}"
echo ""
if [ "$FAIL" -eq 0 ]; then
  echo -e "${GREEN}  ✔ ALL TESTS PASSED — CodeRank integration suite green!${NC}"
  exit 0
else
  echo -e "${RED}  ✘ $FAIL TEST(S) FAILED — Review output above for details.${NC}"
  exit 1
fi