## [0.2.0] - 2026-03-19
### Added
- Vert.x Web engine (Netty-backed event loop)
- `@Blocking` annotation for safe blocking handlers
- Auto `/docs` (Swagger UI) and `/openapi.json` (OpenAPI 3.0)
- Annotation-driven validation: `@NotBlank @NotNull @Email @Min @Max @Size`
- Clean error responses via `HTTPException`
- Field-level validation error details via `ValidationException`
- FastAPI-style validation responses (HTTP `422`)
- TCP optimizations: `TcpNoDelay`, `TcpFastOpen`, `ReusePort`

### Changed
- Engine: Javalin + Jetty -> Vert.x Web (Netty)
- `resolveArgs()` updated for Vert.x `RoutingContext` API
- `UserController` updated to demo validation

### Benchmark
- [fill after running `--bench`] req/s in-process
- Target: 50,000+ req/s external wrk benchmark

