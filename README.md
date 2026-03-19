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
Do not put Sonatype credentials (username/password/token) or GPG private keys into `README.md`.

For releasing to Maven Central, follow [`PUBLISHING.md`](./PUBLISHING.md). It includes a `~/.m2/settings.xml` template like:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>SONATYPE_USERNAME</username>
      <password>SONATYPE_PASSWORD_OR_TOKEN</password>
    </server>
  </servers>
</settings>
```

If you keep any local helper file like `sonatype.txt`, it should stay untracked (and is gitignored).

## Benchmark
Run:
`java -jar target/velocityapi-fat.jar --bench`

Target: 50,000+ req/s in-process (wrk-style external benchmarks are expected to hit ~25-35k req/s depending on setup).
> 8e3mtajdn3


