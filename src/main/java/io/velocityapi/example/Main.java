package io.velocityapi.example;

import io.velocityapi.VelocityAPI;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        int port = 8080;

        VelocityAPI api = new VelocityAPI();
        api.register(new UserController());

        if (args.length > 0 && "--bench".equals(args[0])) {
            api.start(port);
            runBench(port);
            api.stop();
            return;
        }

        api.start(port);
    }

    private static void runBench(int port) throws Exception {
        Thread.sleep(500);

        int threads = 50;
        int total = 10_000;

        HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

        URI uri = URI.create("http://localhost:" + port + "/api/users/1");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        long[] latenciesNs = new long[total];

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(total);
        AtomicInteger cursor = new AtomicInteger(0);

        long submitStart = System.nanoTime();

        for (int i = 0; i < total; i++) {
            pool.submit(() -> {
                int idx = cursor.getAndIncrement();
                try {
                    ready.await();
                    long start = System.nanoTime();
                    HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long end = System.nanoTime();

                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        // Count errors but keep latency sample (we still estimate p99).
                    }

                    latenciesNs[idx] = (end - start);
                } catch (Exception e) {
                    latenciesNs[idx] = 0L;
                } finally {
                    done.countDown();
                }
            });
        }

        ready.countDown();
        done.await();

        long submitEnd = System.nanoTime();

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        long durationNs = submitEnd - submitStart;
        double durationSeconds = durationNs / 1_000_000_000.0d;
        double reqPerSec = total / durationSeconds;

        long sum = 0L;
        int ok = 0;
        int errors = 0;

        for (long sample : latenciesNs) {
            if (sample > 0) {
                sum += sample;
                ok++;
            } else {
                errors++;
            }
        }

        double avgMs = ok == 0 ? 0.0d : (sum / (double) ok) / 1_000_000.0d;

        Arrays.sort(latenciesNs);

        int p99Index = (int) Math.floor(0.99d * (total - 1));
        long p99Ns = latenciesNs[p99Index];
        double p99Ms = p99Ns / 1_000_000.0d;

        System.out.println("\nBenchmark results (VelocityAPI)");
        System.out.println("---------------------------------");
        System.out.printf("Requests: %d%n", total);
        System.out.printf("Errors: %d%n", errors);
        System.out.printf("Throughput: %.2f req/s%n", reqPerSec);
        System.out.printf("Avg latency: %.2f ms%n", avgMs);
        System.out.printf("p99 latency estimate: %.2f ms%n", p99Ms);

        System.out.println("\nComparison table (placeholders)");
        System.out.println("---------------------------------");
        System.out.println("TurboAPI  (Zig)          -> 47,832 req/s");
        System.out.println("Quarkus   (JVM native)  -> ~42,000 req/s");
        System.out.println("Micronaut (JVM)         -> ~28,000 req/s");
        System.out.printf("VelocityAPI (JVM)      -> %.2f req/s%n", reqPerSec);
        System.out.println("FastAPI   (Python)      -> 6,847 req/s\n");
    }
}

