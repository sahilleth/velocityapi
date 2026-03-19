package io.velocityapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.velocityapi.annotations.Body;
import io.velocityapi.annotations.Blocking;
import io.velocityapi.annotations.Controller;
import io.velocityapi.annotations.DELETE;
import io.velocityapi.annotations.GET;
import io.velocityapi.annotations.POST;
import io.velocityapi.annotations.PUT;
import io.velocityapi.annotations.Param;
import io.velocityapi.annotations.Query;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * VelocityAPI: thin annotation layer on top of Vert.x Web.
 */
public final class VelocityAPI {
    // GOLDEN RULE: Never block the event loop.
    // Annotate any method doing DB calls, file I/O, or sleep with @Blocking.
    // Everything else runs on the event loop.

    private enum ScalarKind { INT, LONG, DOUBLE, BOOLEAN, STRING }

    private static final class ArgSpec {
        enum Kind { CTX, BODY, PARAM, QUERY }

        private final Kind kind;
        private final String name; // path/query param name
        private final Class<?> targetType; // scalar type or body type
        private final ScalarKind scalarKind; // null for non-scalar args

        private ArgSpec(Kind kind, String name, Class<?> targetType, ScalarKind scalarKind) {
            this.kind = kind;
            this.name = name;
            this.targetType = targetType;
            this.scalarKind = scalarKind;
        }

        static ArgSpec ctx() {
            return new ArgSpec(Kind.CTX, null, RoutingContext.class, null);
        }

        static ArgSpec body(Class<?> bodyType) {
            return new ArgSpec(Kind.BODY, null, bodyType, null);
        }

        static ArgSpec param(String name, Class<?> targetType, ScalarKind scalarKind) {
            return new ArgSpec(Kind.PARAM, name, targetType, scalarKind);
        }

        static ArgSpec query(String name, Class<?> targetType, ScalarKind scalarKind) {
            return new ArgSpec(Kind.QUERY, name, targetType, scalarKind);
        }
    }

    private record RouteBinding(String httpMethod, String openApiPath, String vertxPath, Method method, Object controller, ArgSpec[] argSpecs) {
        int paramCount() {
            int count = 0;
            int idx = 0;
            while (true) {
                idx = openApiPath.indexOf('{', idx);
                if (idx < 0) return count;
                count++;
                idx++;
            }
        }

        int pathLength() {
            return openApiPath.length();
        }
    }

    private final Vertx vertx;
    private final Router router;
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    private final List<RouteDefinition> routes = new ArrayList<>();

    private int port;

    public VelocityAPI() {
        VertxOptions options = new VertxOptions()
            .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors());

        this.vertx = Vertx.vertx(options);
        this.router = Router.router(vertx);

        // Required so ctx.body() works for @Body.
        router.route().handler(BodyHandler.create());
    }

    public int port() {
        return this.port;
    }

    public VelocityAPI register(Object controller) {
        Class<?> controllerClass = controller.getClass();

        Controller controllerAnn = controllerClass.getAnnotation(Controller.class);
        if (controllerAnn == null) {
            throw new IllegalArgumentException("Controller instance is missing @Controller");
        }

        String basePath = normalizePath(controllerAnn.value());

        // Specific routes before dynamic routes to avoid matching conflicts.
        List<RouteBinding> bindings = new ArrayList<>();

        for (Method method : controllerClass.getDeclaredMethods()) {
            String httpMethod = httpMethodFor(method);
            if (httpMethod == null) continue;

            String suffix = routeSuffixFor(method);
            String fullPath = joinPaths(basePath, suffix); // Keep `{id}` for OpenAPI output.
            String vertxPath = toVertxPath(fullPath); // Convert `{id}` -> `:id` for Vert.x routing.

            routes.add(new RouteDefinition(httpMethod, fullPath, method));

            method.setAccessible(true);
            ArgSpec[] argSpecs = buildArgSpecs(method);
            bindings.add(new RouteBinding(httpMethod, fullPath, vertxPath, method, controller, argSpecs));
        }

        bindings.sort(Comparator
            .comparingInt((RouteBinding b) -> b.paramCount())
            .thenComparing((RouteBinding b) -> b.pathLength(), Comparator.reverseOrder())
            .thenComparing(b -> b.httpMethod)
            .thenComparing(b -> b.openApiPath));

        for (RouteBinding b : bindings) {
            bindRoute(b);
        }

        return this;
    }

    private void bindRoute(RouteBinding b) {
        Method method = b.method;
        Object controller = b.controller;
        boolean isBlocking = method.isAnnotationPresent(Blocking.class);

        var handler = (io.vertx.core.Handler<RoutingContext>) ctx -> {
            if (isBlocking) {
                vertx.executeBlocking((Promise<Object> promise) -> {
                    try {
                        Object[] args = resolveArgs(b.argSpecs, ctx);
                        Object result = method.invoke(controller, args);
                        promise.complete(result);
                    } catch (InvocationTargetException e) {
                        promise.fail(e.getTargetException());
                    } catch (Throwable t) {
                        promise.fail(t);
                    }
                }, ar -> {
                    if (ar.succeeded()) {
                        sendResponse(ctx, ar.result());
                    } else {
                        handleThrowable(ctx, ar.cause());
                    }
                });
                return;
            }

            try {
                Object[] args = resolveArgs(b.argSpecs, ctx);
                Object result = method.invoke(controller, args);
                sendResponse(ctx, result);
            } catch (InvocationTargetException e) {
                handleThrowable(ctx, e.getTargetException());
            } catch (HTTPException e) {
                sendError(ctx, e.statusCode(), e.getMessage());
            } catch (ValidationException e) {
                sendValidationError(ctx, e.getErrors());
            } catch (Throwable t) {
                ctx.fail(500, t);
            }
        };

        switch (b.httpMethod) {
            case "GET" -> router.get(b.vertxPath).handler(handler);
            case "POST" -> router.post(b.vertxPath).handler(handler);
            case "PUT" -> router.put(b.vertxPath).handler(handler);
            case "DELETE" -> router.delete(b.vertxPath).handler(handler);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + b.httpMethod);
        }
    }

    private void handleThrowable(RoutingContext ctx, Throwable t) {
        if (t instanceof HTTPException e) {
            sendError(ctx, e.statusCode(), e.getMessage());
            return;
        }
        if (t instanceof ValidationException e) {
            sendValidationError(ctx, e.getErrors());
            return;
        }

        ctx.fail(500, t);
    }

    private String httpMethodFor(Method method) {
        if (method.isAnnotationPresent(GET.class)) return "GET";
        if (method.isAnnotationPresent(POST.class)) return "POST";
        if (method.isAnnotationPresent(PUT.class)) return "PUT";
        if (method.isAnnotationPresent(DELETE.class)) return "DELETE";
        return null;
    }

    private String routeSuffixFor(Method method) {
        if (method.isAnnotationPresent(GET.class)) return method.getAnnotation(GET.class).value();
        if (method.isAnnotationPresent(POST.class)) return method.getAnnotation(POST.class).value();
        if (method.isAnnotationPresent(PUT.class)) return method.getAnnotation(PUT.class).value();
        if (method.isAnnotationPresent(DELETE.class)) return method.getAnnotation(DELETE.class).value();
        throw new IllegalStateException("No supported route annotation present");
    }

    private Object[] resolveArgs(ArgSpec[] specs, RoutingContext ctx) {
        Object[] args = new Object[specs.length];

        for (int i = 0; i < specs.length; i++) {
            ArgSpec spec = specs[i];

            switch (spec.kind) {
                case CTX -> args[i] = ctx;
                case BODY -> {
                    try {
                        Object body = mapper.readValue(ctx.body().asString(), spec.targetType);
                        Validator.validate(body);
                        args[i] = body;
                    } catch (ValidationException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to deserialize @Body", e);
                    }
                }
                case PARAM -> {
                    String raw = ctx.pathParam(spec.name);
                    args[i] = parseScalar(raw, spec.targetType, spec.scalarKind);
                }
                case QUERY -> {
                    String raw = ctx.queryParams().getAll(spec.name).stream().findFirst().orElse(null);
                    args[i] = parseScalar(raw, spec.targetType, spec.scalarKind);
                }
                default -> throw new IllegalStateException("Unknown arg spec kind: " + spec.kind);
            }
        }

        return args;
    }

    private Object parseScalar(String raw, Class<?> targetType, ScalarKind scalarKind) {
        if (raw == null) {
            return targetType.isPrimitive() ? primitiveDefault(targetType) : null;
        }

        return switch (scalarKind) {
            case STRING -> raw;
            case INT -> Integer.parseInt(raw);
            case LONG -> Long.parseLong(raw);
            case DOUBLE -> Double.parseDouble(raw);
            case BOOLEAN -> Boolean.parseBoolean(raw);
        };
    }

    private ArgSpec[] buildArgSpecs(Method method) {
        Parameter[] params = method.getParameters();
        ArgSpec[] specs = new ArgSpec[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];

            if (p.getType().equals(RoutingContext.class)) {
                specs[i] = ArgSpec.ctx();
                continue;
            }

            if (p.isAnnotationPresent(Body.class)) {
                specs[i] = ArgSpec.body(p.getType());
                continue;
            }

            if (p.isAnnotationPresent(Param.class)) {
                Param ann = p.getAnnotation(Param.class);
                ScalarKind kind = scalarKindFor(p.getType());
                specs[i] = ArgSpec.param(ann.value(), p.getType(), kind);
                continue;
            }

            if (p.isAnnotationPresent(Query.class)) {
                Query ann = p.getAnnotation(Query.class);
                ScalarKind kind = scalarKindFor(p.getType());
                specs[i] = ArgSpec.query(ann.value(), p.getType(), kind);
                continue;
            }

            throw new IllegalArgumentException(
                "Unsupported parameter. Only RoutingContext, @Body, @Param, and @Query are allowed: " + p.getName()
            );
        }

        return specs;
    }

    private static ScalarKind scalarKindFor(Class<?> type) {
        if (type.equals(String.class)) return ScalarKind.STRING;
        if (type.equals(int.class) || type.equals(Integer.class)) return ScalarKind.INT;
        if (type.equals(long.class) || type.equals(Long.class)) return ScalarKind.LONG;
        if (type.equals(double.class) || type.equals(Double.class)) return ScalarKind.DOUBLE;
        if (type.equals(boolean.class) || type.equals(Boolean.class)) return ScalarKind.BOOLEAN;
        throw new IllegalArgumentException("Unsupported parameter type for coercion: " + type.getName());
    }

    private static Object primitiveDefault(Class<?> primitiveType) {
        if (primitiveType.equals(int.class)) return 0;
        if (primitiveType.equals(long.class)) return 0L;
        if (primitiveType.equals(double.class)) return 0.0d;
        if (primitiveType.equals(boolean.class)) return false;
        throw new IllegalArgumentException("Unsupported primitive type: " + primitiveType.getName());
    }

    private void sendResponse(RoutingContext ctx, Object result) {
        if (result == null) {
            ctx.response().setStatusCode(204).end();
            return;
        }

        if (result instanceof String s) {
            ctx.response().end(s);
            return;
        }

        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(writeJson(result));
    }

    private void sendError(RoutingContext ctx, int status, String msg) {
        ctx.response()
            .setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(writeJson(Map.of("status", status, "detail", msg)));
    }

    private void sendValidationError(RoutingContext ctx, List<ValidationError> errors) {
        ctx.response()
            .setStatusCode(422)
            .putHeader("Content-Type", "application/json")
            .end(writeJson(Map.of(
                "status", 422,
                "detail", "Validation failed",
                "errors", errors
            )));
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    public void start(int port) {
        CountDownLatch started = new CountDownLatch(1);

        // Generate OpenAPI + mount docs routes before listening.
        String spec = new OpenAPIGenerator(routes, new HashMap<>()).generate();

        // Convenience: root redirects to docs.
        router.get("/").handler(ctx ->
            ctx.response()
                .setStatusCode(302)
                .putHeader("Location", "/docs")
                .end()
        );

        router.get("/openapi.json").handler(ctx ->
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(spec)
        );

        router.get("/docs").handler(ctx ->
            ctx.response()
                .putHeader("Content-Type", "text/html")
                .end(swaggerUIHtml())
        );

        HttpServerOptions options = new HttpServerOptions()
            .setPort(port)
            // Requested: TCP optimizations.
            .setTcpNoDelay(true)
            .setTcpFastOpen(true)
            .setReusePort(true);

        this.server = vertx.createHttpServer(options);

        server.requestHandler(router).listen(port, ar -> {
            if (ar.succeeded()) {
                this.port = ar.result().actualPort();
                printBannerAndRoutes();
                started.countDown();
            } else {
                throw new RuntimeException("Failed to start", ar.cause());
            }
        });

        awaitLatch(started);
    }

    public void stop() {
        CountDownLatch stopped = new CountDownLatch(1);

        if (server == null) {
            vertx.close(v -> {
                stopped.countDown();
            });
            awaitLatch(stopped);
            return;
        }

        server.close(v -> vertx.close(vv -> stopped.countDown()));
        awaitLatch(stopped);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }

    private void printBannerAndRoutes() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("  __      _____  ____  ____\n");
        sb.append(" / /_   _/ ___/ / __ \\/ __ \\\n");
        sb.append("/ '_/  / /__/ / /_/ / /_/ /\n");
        sb.append("/_/    \\___/  \\____/\\____/\n");
        sb.append("\n");
        sb.append("Listening on http://localhost:").append(port).append("\n");
        sb.append("Docs: http://localhost:").append(port).append("/docs\n");
        sb.append("OpenAPI: http://localhost:").append(port).append("/openapi.json\n");
        sb.append("\n");
        sb.append("Routes:\n");

        routes.stream()
            .sorted(Comparator.comparing(RouteDefinition::httpMethod).thenComparing(RouteDefinition::path))
            .forEach(r -> sb.append("  ").append(r.httpMethod()).append(" ").append(r.path()).append("\n"));

        System.out.print(sb);
    }

    private static String swaggerUIHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <title>VelocityAPI Docs</title>
              <meta charset="utf-8"/>
              <!-- Use jsDelivr CDN (often more reliable than unpkg in restricted networks). -->
              <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css"/>
            </head>
            <body>
              <div id="swagger-ui"></div>
              <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
              <script>
                (function () {
                  function boot() {
                    if (typeof SwaggerUIBundle === 'undefined') {
                      document.body.innerHTML =
                        '<pre>SwaggerUIBundle failed to load. Check network access to the Swagger CDN.</pre>';
                      return;
                    }

                    SwaggerUIBundle({
                      url: "/openapi.json",
                      dom_id: "#swagger-ui",
                      presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset]
                    });
                  }

                  if (document.readyState === 'complete') boot();
                  else window.addEventListener('load', boot);
                })();
              </script>
            </body>
            </html>
            """;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        String trimmed = path.trim();
        if (trimmed.equals("/")) return "/";
        if (!trimmed.startsWith("/")) trimmed = "/" + trimmed;
        if (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private static String stripTrailingSlash(String p) {
        if (p == null || p.isEmpty()) return "";
        if (p.equals("/")) return "";
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private static String joinPaths(String base, String suffix) {
        String b = normalizePath(base);
        String s = normalizePath(suffix);

        if (b.isEmpty() || b.equals("/")) return s.isEmpty() ? "/" : s;
        if (s.isEmpty() || s.equals("/")) return b;

        return stripTrailingSlash(b) + s;
    }

    private static String toVertxPath(String openApiPath) {
        // Vert.x uses `:param` style route params; Velocity uses `{param}`.
        StringBuilder out = new StringBuilder(openApiPath.length());
        int i = 0;
        while (i < openApiPath.length()) {
            char c = openApiPath.charAt(i);
            if (c == '{') {
                int end = openApiPath.indexOf('}', i + 1);
                if (end > i) {
                    String name = openApiPath.substring(i + 1, end);
                    out.append(':').append(name);
                    i = end + 1;
                    continue;
                }
            }

            out.append(c);
            i++;
        }

        return out.toString();
    }
}

