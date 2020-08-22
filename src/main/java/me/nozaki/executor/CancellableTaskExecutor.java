package me.nozaki.executor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CancellableTaskExecutor {

    private final ScheduledExecutorService es;

    public CancellableTaskExecutor(ScheduledExecutorService es) {
        this.es = es;
    }

    public Execution schedule(Runnable task, long delay, TimeUnit unit) {
        CancellableRunnable runnable = new CancellableRunnable(task);
        ScheduledFuture<?> future = es.schedule(runnable, delay, unit);
        return new Execution(future, runnable);
    }

    public static class Execution {

        private final ScheduledFuture<?> future;
        private final CancellableRunnable runnable;

        Execution(ScheduledFuture<?> future, CancellableRunnable runnable) {
            this.future = future;
            this.runnable = runnable;
        }

        /**
         * @return return true when it is cancelled
         * and it's guaranteed that the task won't get executed. otherwise false
         */
        public boolean cancel() {
            return future.cancel(false) && runnable.cancel();
        }
    }

    private static class CancellableRunnable implements Runnable {

        private final Object lock = new Object();
        private boolean cancelledOrStarted;
        private final Runnable task;

        private CancellableRunnable(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            synchronized (lock) {
                if (cancelledOrStarted) {
                    // cancelled, forget about the task
                    return;
                }

                cancelledOrStarted = true;
            }

            task.run();
        }

        boolean cancel() {
            synchronized (lock) {
                if (cancelledOrStarted) {
                    // the task is already started, failed to cancel
                    return false;
                }

                cancelledOrStarted = true;
            }
            return true;
        }
    }
}
