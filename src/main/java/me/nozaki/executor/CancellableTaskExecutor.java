package me.nozaki.executor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CancellableTaskExecutor {

    private final ScheduledExecutorService es;
    private final Logger log;

    /**
     * For a unit test to replicate a particular timing
     */
    private final Runnable hookBetweenCancels;

    public CancellableTaskExecutor(ScheduledExecutorService es, Logger log) {
        this(es, log, () -> {
            // nop
        });
    }

    // For unit tests
    CancellableTaskExecutor(ScheduledExecutorService es, Logger log, Runnable hookBetweenCancels) {
        this.es = es;
        this.log = log;
        this.hookBetweenCancels = hookBetweenCancels;
    }

    public Execution schedule(Runnable task, long delay, TimeUnit unit) {
        CancellableRunnable runnable = new CancellableRunnable(task);
        ScheduledFuture<?> future = es.schedule(runnable, delay, unit);
        return new Execution(future, runnable);
    }

    public class Execution {

        private final ScheduledFuture<?> future;
        private final CancellableRunnable runnable;

        private Execution(ScheduledFuture<?> future, CancellableRunnable runnable) {
            this.future = future;
            this.runnable = runnable;
        }

        /**
         * @return true when the task has been successfully cancelled and it's guaranteed that
         * the task won't get executed. otherwise false
         */
        public boolean cancel() {
            boolean cancelled = runnable.cancel();
            hookBetweenCancels.run();

            // the return value of this call is unreliable; see https://stackoverflow.com/q/55922874/3591946
            future.cancel(false);

            return cancelled;
        }
    }

    private class CancellableRunnable implements Runnable {

        private final AtomicBoolean cancelledOrStarted = new AtomicBoolean();
        private final Runnable task;

        private CancellableRunnable(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            if (!cancelledOrStarted.compareAndSet(false, true)) {
                return; // cancelled, forget about the task
            }
            try {
                task.run();
            } catch (Throwable e) {
                log.log(Level.WARNING, "Uncaught Exception", e);
            }
        }

        boolean cancel() {
            return cancelledOrStarted.compareAndSet(false, true);
        }
    }
}
