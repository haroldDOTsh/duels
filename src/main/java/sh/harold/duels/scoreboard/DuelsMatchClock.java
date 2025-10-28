package sh.harold.duels.scoreboard;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Simple wall-clock tracker for in-match countdowns.
 */
public final class DuelsMatchClock {
    private final Duration totalDuration;
    private final boolean infinite;
    private Duration accrued;
    private long lastUpdateMillis;
    private boolean frozen;

    private DuelsMatchClock(Duration totalDuration, boolean infinite) {
        this.totalDuration = totalDuration;
        this.infinite = infinite;
        this.accrued = Duration.ZERO;
        this.lastUpdateMillis = System.currentTimeMillis();
        this.frozen = false;
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
        updateAccrued();
        return accrued;
    }

    public Duration remaining() {
        if (infinite) {
            return Duration.ZERO;
        }
        updateAccrued();
        Duration remaining = totalDuration.minus(accrued);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean isExpired() {
        if (infinite) {
            return false;
        }
        updateAccrued();
        return accrued.compareTo(totalDuration) >= 0;
    }

    public void setFrozen(boolean frozen) {
        if (this.frozen == frozen) {
            return;
        }
        updateAccrued();
        this.frozen = frozen;
        this.lastUpdateMillis = System.currentTimeMillis();
    }

    public boolean isFrozen() {
        return frozen;
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

    private void updateAccrued() {
        long now = System.currentTimeMillis();
        if (infinite) {
            lastUpdateMillis = now;
            return;
        }
        if (!frozen) {
            long deltaMillis = Math.max(0L, now - lastUpdateMillis);
            if (deltaMillis > 0L) {
                accrued = accrued.plusMillis(deltaMillis);
                if (accrued.compareTo(totalDuration) > 0) {
                    accrued = totalDuration;
                }
            }
        }
        lastUpdateMillis = now;
    }
}
