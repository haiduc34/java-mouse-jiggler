package mousejiggler;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The jiggling algorithm, isolated from the graphical interface.
 * Changed settings are picked up live via {@link #wake()}.
 */
public final class JiggleEngine {

    private final AtomicReference<Settings> settings;
    private final CursorController cursor;
    private final Consumer<Exception> onError;
    private final Random random = new Random();
    private final Object monitor = new Object();

    private volatile boolean stopRequested;
    private boolean wakeRequested;
    private Thread thread;

    public JiggleEngine(AtomicReference<Settings> settings, CursorController cursor,
                        Consumer<Exception> onError) {
        this.settings = settings;
        this.cursor = cursor;
        this.onError = onError;
    }

    /**
     * Chooses a feasible cardinal move without clamping its distance.
     * Directions that would move the cursor outside the virtual screen are
     * excluded; if none remain, the origin is returned and the move is skipped.
     */
    static Point chooseTarget(Point origin, int distance, Rectangle bounds, Random random) {
        List<Point> feasible = new ArrayList<>(4);
        Point left = new Point(origin.x - distance, origin.y);
        Point right = new Point(origin.x + distance, origin.y);
        Point up = new Point(origin.x, origin.y - distance);
        Point down = new Point(origin.x, origin.y + distance);
        for (Point point : new Point[]{left, right, up, down}) {
            if (bounds.x <= point.x && point.x < bounds.x + bounds.width
                    && bounds.y <= point.y && point.y < bounds.y + bounds.height) {
                feasible.add(point);
            }
        }
        if (feasible.isEmpty()) {
            return origin;
        }
        return feasible.get(random.nextInt(feasible.size()));
    }

    public boolean isRunning() {
        Thread current = thread;
        return current != null && current.isAlive();
    }

    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        stopRequested = false;
        synchronized (monitor) {
            wakeRequested = false;
        }
        thread = new Thread(this::run, "mouse-jiggler");
        thread.setDaemon(true);
        thread.start();
    }

    /** Applies changed settings without waiting for the old timeout. */
    public void wake() {
        synchronized (monitor) {
            wakeRequested = true;
            monitor.notifyAll();
        }
    }

    public synchronized void stop() {
        stopRequested = true;
        synchronized (monitor) {
            monitor.notifyAll();
        }
        Thread current = thread;
        if (current != null && current.isAlive() && current != Thread.currentThread()) {
            try {
                current.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (thread != null && !thread.isAlive()) {
            thread = null;
        }
    }

    /**
     * Waits for a full interval; returns {@code false} when settings changed
     * or a stop was requested, so the caller re-reads the current settings.
     */
    private boolean waitFor(long milliseconds) {
        long deadline = System.nanoTime() + milliseconds * 1_000_000L;
        synchronized (monitor) {
            while (!stopRequested) {
                if (wakeRequested) {
                    wakeRequested = false;
                    return false;
                }
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMs <= 0) {
                    return true;
                }
                try {
                    monitor.wait(remainingMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }

    private void run() {
        try {
            while (!stopRequested) {
                if (!waitFor(settings.get().moveIntervalMs())) {
                    continue;
                }

                Settings current = settings.get();
                Point origin = cursor.getPosition();
                Point target = chooseTarget(origin, current.distance(), cursor.getBounds(), random);
                if (target.equals(origin)) {
                    continue;
                }

                cursor.moveTo(target);

                while (!stopRequested) {
                    if (!settings.get().returnCursor()) {
                        break;
                    }
                    if (!waitFor(settings.get().returnIntervalMs())) {
                        continue;
                    }

                    // A user-initiated move cancels this particular return.
                    if (settings.get().returnCursor() && cursor.getPosition().equals(target)) {
                        cursor.moveTo(origin);
                    }
                    break;
                }
            }
        } catch (Exception error) {
            if (!stopRequested && onError != null) {
                onError.accept(error);
            }
        }
    }
}
