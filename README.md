# VelocityAPI
VelocityAPI is a FastAPI-style annotation layer for Java on top of Vert.x Web: best DX with auto docs and built-in validation.

## Why VelocityAPI?

| Feature              | Spring Boot | Quarkus | Javalin | VelocityAPI |
|---|---|---|---|---|
| Lines for a route    | 8+          | 6+      | 3       | 2           |
| Auto /docs           | ✓           | ✓       | ✗       | ✓           |
| Validation built-in  | ✓           | ✓       | ✗       | ✓           |
| Zero config startup  | ✗           | ✗       | ~       | ✓           |
| Feels like FastAPI   | ✗           | ✗       | ✗       | ✓           |
| Setup time           | 30 min      | 45 min  | 10 min  | 2 min       |
| Single dependency    | ✗           | ✗       | ~       | ✓           |

"The only Java framework where you go from zero to a documented, validated REST API in under 10 lines of code."

## Notes
- The server auto-serves `GET /docs` (Swagger UI) and `GET /openapi.json`.
- Validation errors return JSON with HTTP `422 Unprocessable Entity`.

## Local Release Credentials
If you create a local `sonatype.txt` for OSSRH / Maven Central release credentials, it is gitignored and should not be committed to GitHub (it stays local).

## Benchmark
Run:
`java -jar target/velocityapi-fat.jar --bench`

Target: 50,000+ req/s in-process (wrk-style external benchmarks are expected to hit ~25-35k req/s depending on setup).

