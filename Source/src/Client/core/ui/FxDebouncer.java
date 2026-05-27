package Client.core.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

public class FxDebouncer {
    private final PauseTransition delay;
    private Runnable pendingAction;

    public FxDebouncer(Duration duration) {
        this.delay = new PauseTransition(duration);
        this.delay.setOnFinished(event -> {
            Runnable action = pendingAction;
            pendingAction = null;
            if (action != null) {
                action.run();
            }
        });
    }

    public void run(Runnable action) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> run(action));
            return;
        }
        pendingAction = action;
        delay.playFromStart();
    }

    public void cancel() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::cancel);
            return;
        }
        pendingAction = null;
        delay.stop();
    }
}
