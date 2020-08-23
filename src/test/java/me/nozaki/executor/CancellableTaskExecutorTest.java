package me.nozaki.executor;

import me.nozaki.executor.CancellableTaskExecutor.Execution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CancellableTaskExecutorTest {

    private final ScheduledExecutorService es = Executors.newScheduledThreadPool(1);
    private CancellableTaskExecutor sut;

    @Mock
    private Runnable taskMock;
    private Logger log;

    @BeforeEach
    void setUp() {
        log = spy(Logger.getAnonymousLogger());
        sut = new CancellableTaskExecutor(es, log);
    }

    @Test
    @DisplayName("It executes the task with the delay")
    void delay() {
        AtomicReference<Instant> taskFinishedAt = new AtomicReference<>();
        Runnable task = () -> taskFinishedAt.set(Instant.now());
        Instant scheduledAt = Instant.now();

        sut.schedule(task, 100, TimeUnit.MILLISECONDS);

        waitUntilShutdown();
        assertThat(Duration.between(scheduledAt, taskFinishedAt.get())).isGreaterThan(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("It can cancel the task before it starts running")
    void cancelBeforeRunning() {
        Execution execution = sut.schedule(taskMock, 1, TimeUnit.MINUTES);

        boolean cancel = execution.cancel();

        assertThat(cancel).isTrue();
        waitUntilShutdown();
        verifyNoInteractions(taskMock);
    }

    @Test
    @DisplayName("Cancel works when the task has been launched by the enclosed executor")
    void cancelParticular() {
        Runnable hookBetweenCancels = () -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        };
        this.sut = new CancellableTaskExecutor(es, log, hookBetweenCancels);
        Execution execution = sut.schedule(taskMock, 100, TimeUnit.MILLISECONDS);

        boolean cancel = execution.cancel();

        assertThat(cancel).isTrue();
        waitUntilShutdown();
        verifyNoInteractions(taskMock);
    }

    @Test
    @DisplayName("It cannot cancel the task once it starts running")
    void cancelAfterRunning() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable taskTakesSomeTime = () -> {
            latch.countDown();
            try {
                Thread.sleep(300);
                taskMock.run();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        };
        Execution execution = sut.schedule(taskTakesSomeTime, 0, TimeUnit.MILLISECONDS);
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError();
        }

        boolean cancel = execution.cancel();

        assertThat(cancel).isFalse();
        waitUntilShutdown();
        verify(taskMock).run();
    }

    @Test
    @DisplayName("Uncaught Exception gets logged")
    void log() {
        RuntimeException e = new NullPointerException("catch this");

        sut.schedule(() -> {
            throw e;
        }, 0, TimeUnit.MILLISECONDS);

        waitUntilShutdown();
        verify(log).log(Level.WARNING, "Uncaught Exception", e);
    }

    private void waitUntilShutdown() {
        es.shutdown();
        try {
            es.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @AfterEach
    void tearDown() {
        waitUntilShutdown();
    }
}
