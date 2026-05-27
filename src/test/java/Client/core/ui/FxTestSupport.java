package Client.core.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class FxTestSupport {
    private static final long TIMEOUT_SECONDS = 10;
    private static volatile boolean started;
    private static volatile Throwable startupFailure;

    private FxTestSupport() {
    }

    public static void assumeStarted() {
        if (started) {
            return;
        }
        synchronized (FxTestSupport.class) {
            if (started) {
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            try {
                Platform.startup(latch::countDown);
            } catch (IllegalStateException alreadyStarted) {
                latch.countDown();
            } catch (Throwable error) {
                failure.set(error);
                startupFailure = error;
            }

            try {
                if (failure.get() == null && !latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    failure.set(new AssertionError("JavaFX toolkit startup timed out."));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.set(e);
            }

            startupFailure = failure.get();
            started = startupFailure == null;
        }
        Assumptions.assumeTrue(started, () -> "JavaFX toolkit is not available: " + startupFailure);
    }

    public static <T> T runOnFxThread(Callable<T> action) throws Exception {
        assumeStarted();
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }

        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    public static void runOnFxThread(Runnable action) throws Exception {
        runOnFxThread(() -> {
            action.run();
            return null;
        });
    }
}
