package com.alexeys.translate_allinone.utils.llmapi;

import com.alexeys.translate_allinone.utils.config.pojos.ApiProviderProfile;
import com.alexeys.translate_allinone.utils.llmapi.openai.OpenAIRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class StreamingTerminationTest {
    @Test
    void doneFinishesEvenWhenServerKeepsConnectionOpen() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            ApiProviderProfile profile = new ApiProviderProfile();
            profile.base_url = "http://127.0.0.1:" + server.getAddress().getPort();
            profile.api_key = "local-test";
            profile.model_id = "test";
            Future<List<String>> result = worker.submit(() -> {
                try (Stream<String> stream = new LLM(ProviderSettings.fromProviderProfile(profile))
                        .getStreamingCompletion(List.of(new OpenAIRequest.Message("user", "hello")))) {
                    return stream.toList();
                }
            });
            assertEquals(List.of("你好"), result.get(3, TimeUnit.SECONDS));
            assertEquals(1, release.getCount(), "Completion must not depend on server EOF");
        } finally {
            release.countDown();
            server.stop(0);
            worker.shutdownNow();
        }
    }

    @Test
    void deadlineInterruptsReadBeforeTryingToCloseLockedReader() throws Exception {
        Object readerLock = new Object();
        CountDownLatch reading = new CountDownLatch(1);
        AtomicReference<Runnable> deadline = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            var request = LlmRequestLifecycle.startStreamingRequest(deadline::set);
            var raw = Stream.generate(() -> {
                synchronized (readerLock) {
                    reading.countDown();
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException ex) { throw new RuntimeException(ex); }
                    return "unreachable";
                }
            }).onClose(() -> { synchronized (readerLock) { } });
            try (var stream = request.attach(raw)) { stream.findFirst(); }
            catch (Throwable ex) { failure.set(ex); }
        });
        consumer.setDaemon(true);
        ExecutorService timer = Executors.newSingleThreadExecutor();
        try {
            consumer.start();
            assertTrue(reading.await(3, TimeUnit.SECONDS));
            timer.submit(deadline.get()).get(3, TimeUnit.SECONDS);
            consumer.join(3000);
            assertFalse(consumer.isAlive());
            assertInstanceOf(LLMApiException.class, failure.get());
            assertTrue(failure.get().getMessage().contains("timed out"));
        } finally {
            consumer.interrupt();
            consumer.join(3000);
            timer.shutdownNow();
        }
    }
}
