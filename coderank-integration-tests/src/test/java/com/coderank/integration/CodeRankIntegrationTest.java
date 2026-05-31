package com.coderank.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeRank End-to-End Integration Test Suite
 *
 * Covers:
 *   Path 1 — Admin Setup + Interpreted Execution (Python — Two Sum)
 *   Path 2 — Compiled Execution Flow (Java + C++ — Factorial)
 *   Path 3 — Aggregated Test Matrix Flow (JavaScript — FizzBuzz, 5 test cases)
 *   Negative — RBAC enforcement checks
 *
 * All requests pass through the API Gateway on port 8080.
 * Run with: mvn test -Dtest=CodeRankIntegrationTest
 *
 * Prerequisites:
 *   - Full CodeRank stack running (docker-compose up)
 *   - BASE_URL environment variable optional (default: http://localhost:8080)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CodeRankIntegrationTest {

    // ─── Configuration ────────────────────────────────────────────────────────
    private static final String BASE_URL =
            System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");

    private static final String ADMIN_EMAIL    = "admin@coderank.io";
    private static final String ADMIN_PASSWORD = "Admin@1234";
    private static final String USER_EMAIL     = "testuser_" + Instant.now().toEpochMilli() + "@coderank.io";
    private static final String USER_PASSWORD  = "TestUser@1234";
    private static final String USER_USERNAME  = "testuser_" + Instant.now().toEpochMilli();

    private static final int    POLL_INTERVAL_MS  = 3_000;
    private static final int    MAX_POLL_ATTEMPTS  = 20;

    // ─── Shared state across test methods ─────────────────────────────────────
    private static String adminJwt;
    private static String userJwt;

    private static String problemIdP1;
    private static String problemSlugP1;
    private static String submissionIdP1;

    private static String problemIdP2;
    private static String submissionIdP2Java;
    private static String submissionIdP2Cpp;

    private static String problemIdP3;
    private static String submissionIdP3;

    // ─── Source code constants ─────────────────────────────────────────────────
    private static final String PYTHON_TWO_SUM =
            "import sys\n" +
                    "input_data = sys.stdin.read().split()\n" +
                    "n = int(input_data[0])\n" +
                    "nums = list(map(int, input_data[1:n+1]))\n" +
                    "target = int(input_data[n+1])\n" +
                    "lookup = {}\n" +
                    "for i, num in enumerate(nums):\n" +
                    "    complement = target - num\n" +
                    "    if complement in lookup:\n" +
                    "        print(lookup[complement], i)\n" +
                    "        break\n" +
                    "    lookup[num] = i\n";

    private static final String JAVA_FACTORIAL =
            "import java.util.Scanner;\n" +
                    "public class Main {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        Scanner sc = new Scanner(System.in);\n" +
                    "        int n = sc.nextInt();\n" +
                    "        long result = 1;\n" +
                    "        for (int i = 2; i <= n; i++) {\n" +
                    "            result *= i;\n" +
                    "        }\n" +
                    "        System.out.println(result);\n" +
                    "    }\n" +
                    "}\n";

    private static final String CPP_FACTORIAL =
            "#include <iostream>\n" +
                    "using namespace std;\n" +
                    "int main() {\n" +
                    "    long long n;\n" +
                    "    cin >> n;\n" +
                    "    long long result = 1;\n" +
                    "    for (long long i = 2; i <= n; i++) {\n" +
                    "        result *= i;\n" +
                    "    }\n" +
                    "    cout << result << endl;\n" +
                    "    return 0;\n" +
                    "}\n";

    private static final String JS_FIZZBUZZ =
            "const readline = require('readline');\n" +
                    "const rl = readline.createInterface({ input: process.stdin });\n" +
                    "rl.on('line', (line) => {\n" +
                    "    const n = parseInt(line.trim(), 10);\n" +
                    "    for (let i = 1; i <= n; i++) {\n" +
                    "        if (i % 15 === 0) process.stdout.write('FizzBuzz\\n');\n" +
                    "        else if (i % 3 === 0) process.stdout.write('Fizz\\n');\n" +
                    "        else if (i % 5 === 0) process.stdout.write('Buzz\\n');\n" +
                    "        else process.stdout.write(i + '\\n');\n" +
                    "    }\n" +
                    "    rl.close();\n" +
                    "});\n";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    // =========================================================================
    // PATH 1 — Admin Setup + Interpreted Execution (Python)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("P1-S1: Admin Login — extracts ADMIN_JWT and verifies ROLE_ADMIN")
    void p1_s1_adminLogin() {
        String requestBody = "{\n" +
                "  \"email\": \"" + ADMIN_EMAIL + "\",\n" +
                "  \"password\": \"" + ADMIN_PASSWORD + "\"\n" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("accessToken", not(emptyString()))
                .body("role", equalTo("ROLE_ADMIN"))
                .body("userId", notNullValue())
                .extract().response();

        adminJwt = response.jsonPath().getString("accessToken");
        assertNotNull(adminJwt, "Admin JWT must not be null");
        assertFalse(adminJwt.isBlank(), "Admin JWT must not be blank");
    }

    @Test
    @Order(2)
    @DisplayName("P1-S2: Admin Creates Problem — Two Sum (EASY) — extracts problemId and slug")
    void p1_s2_adminCreatesProblem() {
        assertNotNull(adminJwt, "Admin JWT must be set from P1-S1");

        String requestBody = "{\n" +
                "  \"title\": \"Two Sum\",\n" +
                "  \"description\": \"Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target.\",\n" +
                "  \"difficulty\": \"EASY\",\n" +
                "  \"constraints\": \"2 <= nums.length <= 10^4\\n-10^9 <= nums[i] <= 10^9\",\n" +
                "  \"examples\": [\n" +
                "    {\n" +
                "      \"inputText\": \"nums = [2,7,11,15], target = 9\",\n" +
                "      \"outputText\": \"[0,1]\",\n" +
                "      \"explanation\": \"nums[0] + nums[1] = 9\",\n" +
                "      \"orderIndex\": 1\n" +
                "    }\n" +
                "  ],\n" +
                "  \"testCases\": [\n" +
                "    {\n" +
                "      \"input\": \"2\\n2 7 11 15\\n9\",\n" +
                "      \"expected\": \"0 1\",\n" +
                "      \"isSample\": true,\n" +
                "      \"orderIndex\": 1\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminJwt)
                .body(requestBody)
                .when()
                .post("/api/v1/problems")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("slug", notNullValue())
                .body("title", equalTo("Two Sum"))
                .body("difficulty", equalTo("EASY"))
                .body("testCases", nullValue())
                .extract().response();

        problemIdP1   = response.jsonPath().getString("id");
        problemSlugP1 = response.jsonPath().getString("slug");
        assertNotNull(problemIdP1,   "Problem ID must not be null");
        assertNotNull(problemSlugP1, "Problem slug must not be null");
    }
    @Test
    @Order(3)
    @DisplayName("P1-S3: Register Standard User — extracts USER_JWT and verifies ROLE_USER")
    void p1_s3_registerStandardUser() {
        String requestBody = "{\n" +
                "  \"username\": \"" + USER_USERNAME + "\",\n" +
                "  \"email\": \"" + USER_EMAIL + "\",\n" +
                "  \"password\": \"" + USER_PASSWORD + "\"\n" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("accessToken", not(emptyString()))
                .body("role", equalTo("ROLE_USER"))
                .extract().response();

        userJwt = response.jsonPath().getString("accessToken");
        assertNotNull(userJwt, "User JWT must not be null");
        assertFalse(userJwt.isBlank(), "User JWT must not be blank");
    }
    @Test
    @Order(4)
    @DisplayName("P1-S4: User GETs Problem by Slug — verifies visibility and test case hiding")
    void p1_s4_userGetsProblemBySlug() {
        assertNotNull(userJwt,       "User JWT must be set from P1-S3");
        assertNotNull(problemSlugP1, "Problem slug must be set from P1-S2");

        given()
                .header("Authorization", "Bearer " + userJwt)
                .when()
                .get("/api/v1/problems/{slug}", problemSlugP1)
                .then()
                .statusCode(200)
                .body("id",         equalTo(problemIdP1))
                .body("title",      equalTo("Two Sum"))
                .body("difficulty", equalTo("EASY"))
                // Internal test cases must NOT be exposed to standard users
                .body("testCases",  nullValue())
                // Public examples must be visible
                .body("examples",   hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(5)
    @DisplayName("P1-S5: User Submits Python Code — Two Sum — extracts submissionId")
    void p1_s5_userSubmitsPythonCode() {
        assertNotNull(userJwt,     "User JWT must be set from P1-S3");
        assertNotNull(problemIdP1, "Problem ID must be set from P1-S2");

        String requestBody = "{\n" +
                "  \"language\": \"python\",\n" +
                "  \"sourceCode\": " + escapeJsonString(PYTHON_TWO_SUM) + "\n" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + userJwt)
                .body(requestBody)
                .when()
                .post("/api/v1/problems/{id}/submit", problemIdP1)
                .then()
                .statusCode(202)
                .body("submissionId", notNullValue())
                .body("submissionId", not(emptyString()))
                .body("language",     equalTo("python"))
                .body("status",       oneOf("QUEUED", "PENDING", "RUNNING"))
                .extract().response();

        submissionIdP1 = response.jsonPath().getString("submissionId");
        assertNotNull(submissionIdP1, "Submission ID must not be null");
    }

    @Test
    @Order(6)
    @DisplayName("P1-S6: Poll for Python Verdict — asserts COMPLETED + ACCEPTED")
    void p1_s6_pollForPythonVerdict() throws InterruptedException {
        assertNotNull(userJwt,        "User JWT must be set from P1-S3");
        assertNotNull(submissionIdP1, "Submission ID must be set from P1-S5");

        Response terminalResponse = pollUntilTerminal(submissionIdP1, userJwt, "Path1-Python");

        String status  = terminalResponse.jsonPath().getString("status");
        String verdict = terminalResponse.jsonPath().getString("verdict");
        Object execMs  = terminalResponse.jsonPath().get("executionTimeMs");
        String source  = terminalResponse.jsonPath().getString("source");

        assertEquals("COMPLETED", status,
                "Python execution infrastructure status must be COMPLETED");
        assertEquals("ACCEPTED", verdict,
                "Python verdict must be ACCEPTED — Two Sum solution is correct");
        assertNotNull(execMs,
                "executionTimeMs must be populated after execution");
        assertNotNull(source,
                "source field (cache/db) must be present in response");
    }

    // =========================================================================
    // PATH 2 — Compiled Execution Flow (Java + C++)
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("P2-S1: Admin Creates Problem for Compiled Languages — Factorial (MEDIUM)")
    void p2_s1_adminCreatesFactorialProblem() {
        assertNotNull(adminJwt, "Admin JWT must be set from P1-S1");

        String requestBody = "{\n" +
                "  \"title\": \"Factorial\",\n" +
                "  \"description\": \"Given a non-negative integer n, compute and return n!. Read n from stdin and print the result to stdout.\",\n" +
                "  \"difficulty\": \"MEDIUM\",\n" +
                "  \"constraints\": \"0 <= n <= 12\",\n" +
                "  \"examples\": [\n" +
                "    {\n" +
                "      \"inputText\": \"5\",\n" +
                "      \"outputText\": \"120\",\n" +
                "      \"explanation\": \"5! = 120\",\n" +
                "      \"orderIndex\": 1\n" +
                "    }\n" +
                "  ],\n" +
                "  \"testCases\": [\n" +
                "    {\n" +
                "      \"input\": \"5\",\n" +
                "      \"expected\": \"120\",\n" +
                "      \"isSample\": true,\n" +
                "      \"orderIndex\": 1\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminJwt)
                .body(requestBody)
                .when()
                .post("/api/v1/problems")
                .then()
                .statusCode(201)
                .body("id",         notNullValue())
                .body("title",      equalTo("Factorial"))
                .body("difficulty", equalTo("MEDIUM"))
                .extract().response();

        problemIdP2 = response.jsonPath().getString("id");
        assertNotNull(problemIdP2, "Problem ID (P2) must not be null");
    }

    @Test
    @Order(8)
    @DisplayName("P2-S2: User Submits Java Code — Factorial — extracts submissionId")
    void p2_s2_userSubmitsJavaCode() {
        assertNotNull(userJwt,     "User JWT must be set from P1-S3");
        assertNotNull(problemIdP2, "Problem ID (P2) must be set from P2-S1");

        String requestBody = "{\n" +
                "  \"language\": \"java\",\n" +
                "  \"sourceCode\": " + escapeJsonString(JAVA_FACTORIAL) + "\n" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + userJwt)
                .body(requestBody)
                .when()
                .post("/api/v1/problems/{id}/submit", problemIdP2)
                .then()
                .statusCode(202)
                .body("submissionId", notNullValue())
                .body("language",     equalTo("java"))
                .body("status",       oneOf("QUEUED", "PENDING", "RUNNING"))
                .extract().response();

        submissionIdP2Java = response.jsonPath().getString("submissionId");
        assertNotNull(submissionIdP2Java, "Java Submission ID must not be null");
    }

    @Test
    @Order(9)
    @DisplayName("P2-S3: Poll for Java Verdict — confirms javac compile + JVM execution → ACCEPTED")
    void p2_s3_pollForJavaVerdict() throws InterruptedException {
        assertNotNull(userJwt,         "User JWT must be set from P1-S3");
        assertNotNull(submissionIdP2Java, "Java Submission ID must be set from P2-S2");

        Response terminalResponse = pollUntilTerminal(submissionIdP2Java, userJwt, "Path2-Java");

        String status  = terminalResponse.jsonPath().getString("status");
        String verdict = terminalResponse.jsonPath().getString("verdict");
        Object execMs  = terminalResponse.jsonPath().get("executionTimeMs");

        assertEquals("COMPLETED", status,
                "Java execution infrastructure status must be COMPLETED");
        assertEquals("ACCEPTED", verdict,
                "Java verdict must be ACCEPTED — confirms javac compilation and JVM binary execution succeeded");
        assertNotEquals("COMPILATION_ERROR", verdict,
                "Java source must not produce a COMPILATION_ERROR");
        assertNotEquals("RUNTIME_ERROR", verdict,
                "Java source must not produce a RUNTIME_ERROR");
        assertNotNull(execMs,
                "Java executionTimeMs must be populated — confirms binary execution timing was captured");
    }

    @Test
    @Order(10)
    @DisplayName("P2-S4: User Submits C++ Code — Factorial — extracts submissionId")
    void p2_s4_userSubmitsCppCode() {
        assertNotNull(userJwt,     "User JWT must be set from P1-S3");
        assertNotNull(problemIdP2, "Problem ID (P2) must be set from P2-S1");

        String requestBody = "{\n" +
                "  \"language\": \"cpp\",\n" +
                "  \"sourceCode\": " + escapeJsonString(CPP_FACTORIAL) + "\n" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + userJwt)
                .body(requestBody)
                .when()
                .post("/api/v1/problems/{id}/submit", problemIdP2)
                .then()
                .statusCode(202)
                .body("submissionId", notNullValue())
                .body("language",     equalTo("cpp"))
                .body("status",       oneOf("QUEUED", "PENDING", "RUNNING"))
                .extract().response();

        submissionIdP2Cpp = response.jsonPath().getString("submissionId");
        assertNotNull(submissionIdP2Cpp, "C++ Submission ID must not be null");
    }

    @Test
    @Order(11)
    @DisplayName("P2-S5: Poll for C++ Verdict — confirms g++ compile + native binary execution → ACCEPTED")
    void p2_s5_pollForCppVerdict() throws InterruptedException {
        assertNotNull(userJwt,        "User JWT must be set from P1-S3");
        assertNotNull(submissionIdP2Cpp, "C++ Submission ID must be set from P2-S4");

        Response terminalResponse = pollUntilTerminal(submissionIdP2Cpp, userJwt, "Path2-Cpp");

        String status  = terminalResponse.jsonPath().getString("status");
        String verdict = terminalResponse.jsonPath().getString("verdict");
        Object execMs  = terminalResponse.jsonPath().get("executionTimeMs");

        assertEquals("COMPLETED", status,
                "C++ execution infrastructure status must be COMPLETED");
        assertEquals("ACCEPTED", verdict,
                "C++ verdict must be ACCEPTED — confirms g++ compilation and native binary execution succeeded");
        assertNotEquals("COMPILATION_ERROR", verdict,
                "C++ source must not produce a COMPILATION_ERROR");
        assertNotNull(execMs,
                "C++ executionTimeMs must be populated — confirms native binary timing was captured");
    }

    // =========================================================================
    // PATH 3 — Aggregated Test Matrix Flow (JavaScript — 5 Test Cases)
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("P3-S1: Admin Creates FizzBuzz Matrix Problem — HARD with 5 distinct test cases")
    void p3_s1_adminCreatesFizzBuzzMatrixProblem() {
        assertNotNull(adminJwt, "Admin JWT must be set from P1-S1");

        String requestBody = "{\n" +
                "  \"title\": \"FizzBuzz Matrix\",\n" +
                "  \"description\": \"Given an integer n, print numbers from 1 to n. For multiples of 3 print Fizz, for multiples of 5 print Buzz, for multiples of both print FizzBuzz.\",\n" +
                "  \"difficulty\": \"HARD\",\n" +
                "  \"constraints\": \"1 <= n <= 100\",\n" +
                "  \"examples\": [\n" +
                "    {\n" +
                "      \"inputText\": \"5\",\n" +
                "      \"outputText\": \"1\\n2\\nFizz\\n4\\nBuzz\",\n" +
                "      \"explanation\": \"3 divisible by 3 = Fizz, 5 divisible by 5 = Buzz\",\n" +
                "      \"orderIndex\": 1\n" +
                "    }\n" +
                "  ],\n" +
                "  \"testCases\": [\n" +
                "    { \"input\": \"5\",  \"expected\": \"1\\n2\\nFizz\\n4\\nBuzz\",                                                              \"isSample\": true,  \"orderIndex\": 1 },\n" +
                "    { \"input\": \"15\", \"expected\": \"1\\n2\\nFizz\\n4\\nBuzz\\nFizz\\n7\\n8\\nFizz\\nBuzz\\n11\\nFizz\\n13\\n14\\nFizzBuzz\", \"isSample\": false, \"orderIndex\": 2 },\n" +
                "    { \"input\": \"1\",  \"expected\": \"1\",                                                                                    \"isSample\": false, \"orderIndex\": 3 },\n" +
                "    { \"input\": \"3\",  \"expected\": \"1\\n2\\nFizz\",                                                                         \"isSample\": false, \"orderIndex\": 4 },\n" +
                "    { \"input\": \"10\", \"expected\": \"1\\n2\\nFizz\\n4\\nBuzz\\nFizz\\n7\\n8\\nFizz\\nBuzz\",                               \"isSample\": false, \"orderIndex\": 5 }\n" +
                "  ]\n" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminJwt)
                .body(requestBody)
                .when()
                .post("/api/v1/problems")
                .then()
                .statusCode(201)
                .body("id",         notNullValue())
                .body("title",      equalTo("FizzBuzz Matrix"))
                .body("difficulty", equalTo("HARD"))
                .extract().response();

        problemIdP3 = response.jsonPath().getString("id");
        assertNotNull(problemIdP3, "Problem ID (P3) must not be null");
    }

    @Test
    @Order(13)
    @DisplayName("P3-S2: User Submits JavaScript Code — FizzBuzz Matrix — extracts submissionId")
    void p3_s2_userSubmitsJavaScriptCode() {
        assertNotNull(userJwt,     "User JWT must be set from P1-S3");
        assertNotNull(problemIdP3, "Problem ID (P3) must be set from P3-S1");

        String requestBody = "{\n" +
                "  \"language\": \"javascript\",\n" +
                "  \"sourceCode\": " + escapeJsonString(JS_FIZZBUZZ) + "\n" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + userJwt)
                .body(requestBody)
                .when()
                .post("/api/v1/problems/{id}/submit", problemIdP3)
                .then()
                .statusCode(202)
                .body("submissionId", notNullValue())
                .body("language",     equalTo("javascript"))
                .body("status",       oneOf("QUEUED", "PENDING", "RUNNING"))
                .extract().response();

        submissionIdP3 = response.jsonPath().getString("submissionId");
        assertNotNull(submissionIdP3, "JavaScript Matrix Submission ID must not be null");
    }

    @Test
    @Order(14)
    @DisplayName("P3-S3: Poll for Matrix Verdict — asserts all 5 test cases aggregated → ACCEPTED")
    void p3_s3_pollForMatrixAggregatedVerdict() throws InterruptedException {
        assertNotNull(userJwt,       "User JWT must be set from P1-S3");
        assertNotNull(submissionIdP3, "Matrix Submission ID must be set from P3-S2");

        Response terminalResponse = pollUntilTerminal(submissionIdP3, userJwt, "Path3-JSMatrix");

        String status      = terminalResponse.jsonPath().getString("status");
        String verdict     = terminalResponse.jsonPath().getString("verdict");
        Object execMs      = terminalResponse.jsonPath().get("executionTimeMs");
        String completedAt = terminalResponse.jsonPath().getString("completedAt");
        String source      = terminalResponse.jsonPath().getString("source");

        assertEquals("COMPLETED", status,
                "Matrix execution infrastructure status must be COMPLETED");
        assertEquals("ACCEPTED", verdict,
                "Aggregated verdict across all 5 FizzBuzz test cases must be ACCEPTED");
        assertNotNull(execMs,
                "executionTimeMs must be populated — confirms downstream aggregation of all test case timings");

        // executionTimeMs must be > 0 (not a zero-time synthetic response)
        int execMsInt = terminalResponse.jsonPath().getInt("executionTimeMs");
        assertTrue(execMsInt > 0,
                "executionTimeMs must be greater than 0 after real Docker execution");

        assertNotNull(completedAt,
                "completedAt timestamp must be populated for a terminal result");
        assertNotNull(source,
                "source field (cache/db) must be present — confirms result was stored and retrieved");
    }

    @Test
    @Order(15)
    @DisplayName("P3-S4: Verify JavaScript Matrix Submission Listed in My Submissions")
    void p3_s4_verifySubmissionInMySubmissions() {
        assertNotNull(userJwt,       "User JWT must be set from P1-S3");
        assertNotNull(problemIdP3,   "Problem ID (P3) must be set from P3-S1");
        assertNotNull(submissionIdP3, "Submission ID (P3) must be set from P3-S2");

        given()
                .header("Authorization", "Bearer " + userJwt)
                .queryParam("problemId", problemIdP3)
                .when()
                .get("/api/v1/submissions")
                .then()
                .statusCode(200)
                .body("content",             notNullValue())
                .body("content.size()",      greaterThanOrEqualTo(1))
                .body("content.problemId",   hasItem(problemIdP3))
                .body("content.submissionId",hasItem(submissionIdP3));
    }

    // =========================================================================
    // NEGATIVE — RBAC Enforcement Checks
    // =========================================================================

    @Test
    @Order(16)
    @DisplayName("NEG-1: Unauthenticated request to protected endpoint → 401 MISSING_TOKEN")
    void neg1_unauthenticatedRequestReturns401() {
        given()
                .when()
                .get("/api/v1/problems")
                .then()
                .statusCode(401)
                .body("error", equalTo("MISSING_TOKEN"));
    }

    @Test
    @Order(17)
    @DisplayName("NEG-2: Standard User attempts Problem creation → 403 Forbidden (RBAC enforced)")
    void neg2_standardUserCannotCreateProblem() {
        assertNotNull(userJwt, "User JWT must be set from P1-S3");

        String requestBody = "{\n" +
                "  \"title\": \"Unauthorized Create Attempt\",\n" +
                "  \"description\": \"This request must be rejected by RBAC.\",\n" +
                "  \"difficulty\": \"EASY\",\n" +
                "  \"testCases\": [\n" +
                "    { \"input\": \"1\", \"expected\": \"1\", \"isSample\": true, \"orderIndex\": 1 }\n" +
                "  ]\n" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + userJwt)
                .body(requestBody)
                .when()
                .post("/api/v1/problems")
                .then()
                .statusCode(403);
    }

    @Test
    @Order(18)
    @DisplayName("NEG-3: Internal endpoint blocked at Gateway → 403 (even with Admin JWT)")
    void neg3_internalEndpointBlockedAtGateway() {
        assertNotNull(adminJwt,    "Admin JWT must be set from P1-S1");
        assertNotNull(problemIdP1, "Problem ID (P1) must be set from P1-S2");

        // /api/v1/internal/* routes must be blocked by the Gateway
        // regardless of the caller's role or token validity
        given()
                .header("Authorization", "Bearer " + adminJwt)
                .when()
                .get("/api/v1/internal/problems/{id}", problemIdP1)
                .then()
                .statusCode(403);
    }

    @Test
    @Order(19)
    @DisplayName("NEG-4: Expired/Malformed JWT → 401 INVALID_TOKEN")
    void neg4_malformedJwtReturns401() {
        given()
                .header("Authorization", "Bearer this.is.not.a.valid.jwt.token")
                .when()
                .get("/api/v1/problems")
                .then()
                .statusCode(401)
                .body("error", equalTo("INVALID_TOKEN"));
    }

    @Test
    @Order(20)
    @DisplayName("NEG-5: User cannot fetch another user's submission result → 403 or 404")
    void neg5_userCannotFetchOtherUserSubmission() {
        assertNotNull(userJwt, "User JWT must be set from P1-S3");

        // A random UUID that belongs to no one — must return 404 (not found)
        // or 403 (ownership check). Both are acceptable security outcomes.
        String randomSubmissionId = UUID.randomUUID().toString();

        int statusCode = given()
                .header("Authorization", "Bearer " + userJwt)
                .when()
                .get("/api/v1/submissions/{id}/result", randomSubmissionId)
                .then()
                .extract().statusCode();

        assertTrue(
                statusCode == 403 || statusCode == 404,
                "Fetching a non-existent or unowned submission must return 403 or 404, got: " + statusCode
        );
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Polls the submission result endpoint until the job reaches a terminal
     * ExecutionStatus (COMPLETED, FAILED, or TIMEDOUT), or fails the test
     * if the maximum number of attempts is exceeded.
     *
     * @param submissionId  the submission UUID to poll
     * @param jwt           the Bearer token of the requesting user
     * @param label         a human-readable label for failure messages
     * @return the terminal Response containing status and verdict
     */
    private Response pollUntilTerminal(String submissionId, String jwt, String label)
            throws InterruptedException {

        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            Response response = given()
                    .header("Authorization", "Bearer " + jwt)
                    .when()
                    .get("/api/v1/submissions/{id}/result", submissionId)
                    .then()
                    .statusCode(200)
                    .extract().response();

            String status = response.jsonPath().getString("status");
            System.out.printf("[%s] Poll attempt %d/%d — status: %s%n",
                    label, attempt, MAX_POLL_ATTEMPTS, status);

            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "TIMEDOUT".equals(status)) {
                return response;
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }

        fail("[" + label + "] Polling timed out after " +
                (MAX_POLL_ATTEMPTS * POLL_INTERVAL_MS / 1000) + "s — job never reached a terminal state");
        return null; // unreachable — fail() throws AssertionError
    }

    /**
     * Serialises a multi-line Java string into a JSON string literal
     * by delegating to Jackson-style escaping rules:
     * newlines → \n, quotes → \", backslashes → \\
     *
     * @param raw the raw source code string
     * @return a JSON-safe quoted string including surrounding double-quotes
     */
    private static String escapeJsonString(String raw) {
        String escaped = raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
