package sh.harold.duels.scoreboard;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Simple wall-clock tracker for in-match countdowns.
 */
public final class DuelsMatchClock {
    private final long startTimeMillis;
    private final Duration totalDuration;
    private final boolean infinite;

    private DuelsMatchClock(Duration totalDuration, boolean infinite) {
        this.startTimeMillis = System.currentTimeMillis();
        this.totalDuration = totalDuration;
        this.infinite = infinite;
    }

    public static DuelsMatchClock start(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Match duration must be positive");
        }
        return new DuelsMatchClock(duration, false);
    }

    public static DuelsMatchClock infinite() {
        return new DuelsMatchClock(Duration.ZERO, true);
    }

    public Optional<Duration> totalDuration() {
        return infinite ? Optional.empty() : Optional.of(totalDuration);
    }

    public boolean isInfinite() {
        return infinite;
    }

    public Duration elapsed() {
        long deltaMillis = Math.max(0L, System.currentTimeMillis() - startTimeMillis);
        return Duration.ofMillis(deltaMillis);
    }

    public Duration remaining() {
        if (infinite) {
            return Duration.ZERO;
        }
        Duration remaining = totalDuration.minus(elapsed());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean isExpired() {
        return !infinite && remaining().isZero();
    }

    public String formatRemaining() {
        if (infinite) {
            return "∞";
        }
        Duration remaining = remaining();
        long totalSeconds = remaining.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
