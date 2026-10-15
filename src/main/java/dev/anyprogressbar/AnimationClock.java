package dev.anyprogressbar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.Timer;

/**
 * One shared animation clock for every progress bar: a single timer and a single start time.
 * The timer only runs while at least one registered client reports that it is animating.
 * Must be used from the Swing event thread.
 */
final class AnimationClock {
    interface Client {
        /** True while the client needs repaints to keep moving (false for idle or finished bars). */
        boolean isAnimating();
        void tick();
    }

    private static final int PERIOD_MS = 33;
    private static final AnimationClock INSTANCE = new AnimationClock();

    private final long started = System.nanoTime();
    private final Set<Client> clients = new LinkedHashSet<>();
    private final Timer timer = new Timer(PERIOD_MS, e -> tick());

    private AnimationClock() {}

    static AnimationClock getInstance() { return INSTANCE; }

    /** Seconds since the clock was created; every bar reads the same value so they animate in sync. */
    double seconds() { return (System.nanoTime() - started) / 1_000_000_000.0; }

    void register(Client client) {
        clients.add(client);
        wake();
    }

    void unregister(Client client) {
        clients.remove(client);
        if (clients.isEmpty()) timer.stop();
    }

    /** Call when a client may have started animating again, e.g. its value changed. */
    void wake() {
        if (!timer.isRunning() && clients.stream().anyMatch(Client::isAnimating)) timer.start();
    }

    boolean isRunning() { return timer.isRunning(); }

    private void tick() {
        boolean any = false;
        for (Client client : new ArrayList<>(clients)) {
            if (client.isAnimating()) {
                any = true;
                client.tick();
            }
        }
        if (!any) timer.stop();
    }
}
