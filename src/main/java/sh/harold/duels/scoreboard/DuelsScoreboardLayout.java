package sh.harold.duels.scoreboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import sh.harold.fulcrum.api.message.scoreboard.module.ScoreboardModule;
import sh.harold.fulcrum.minigame.state.context.StateContext;

public final class DuelsScoreboardLayout {
    private final String title;
    private final Optional<String> headerLabel;
    private final String familyDisplayName;
    private final String variantDisplayName;
    private final Optional<Duration> matchDuration;
    private final Optional<ScoreboardModuleFactory> secondModuleFactory;
    private final List<ScoreboardModuleFactory> betweenModuleFactories;

    private DuelsScoreboardLayout(Builder builder) {
        this.title = builder.title;
        this.headerLabel = builder.headerLabel;
        this.familyDisplayName = builder.familyDisplayName;
        this.variantDisplayName = builder.variantDisplayName;
        this.matchDuration = builder.matchDuration;
        this.secondModuleFactory = builder.secondModuleFactory;
        this.betweenModuleFactories = Collections.unmodifiableList(new ArrayList<>(builder.betweenModuleFactories));
    }

    public String title() {
        return title;
    }

    public Optional<String> headerLabel() {
        return headerLabel;
    }

    public String familyDisplayName() {
        return familyDisplayName;
    }

    public String variantDisplayName() {
        return variantDisplayName;
    }

    public Optional<Duration> matchDuration() {
        return matchDuration;
    }

    public Optional<ScoreboardModuleFactory> secondModuleFactory() {
        return secondModuleFactory;
    }

    public List<ScoreboardModuleFactory> betweenModuleFactories() {
        return betweenModuleFactories;
    }

    public static Builder builder(String familyDisplayName, String variantDisplayName) {
        return new Builder(familyDisplayName, variantDisplayName);
    }

    @FunctionalInterface
    public interface ScoreboardModuleFactory {
        ScoreboardModule create(StateContext context, DuelsMatchClock clock);
    }

    public static final class Builder {
        private String title = "&eDUELS";
        private Optional<String> headerLabel = Optional.empty();
        private final String familyDisplayName;
        private final String variantDisplayName;
        private Optional<Duration> matchDuration = Optional.empty();
        private Optional<ScoreboardModuleFactory> secondModuleFactory = Optional.empty();
        private final List<ScoreboardModuleFactory> betweenModuleFactories = new ArrayList<>();

        private Builder(String familyDisplayName, String variantDisplayName) {
            this.familyDisplayName = Objects.requireNonNull(familyDisplayName, "familyDisplayName");
            this.variantDisplayName = Objects.requireNonNull(variantDisplayName, "variantDisplayName");
        }

        public Builder title(String title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        public Builder headerLabel(String headerLabel) {
            if (headerLabel == null || headerLabel.isBlank()) {
                this.headerLabel = Optional.empty();
            } else {
                this.headerLabel = Optional.of(headerLabel);
            }
            return this;
        }

        public Builder matchDuration(Duration duration) {
            this.matchDuration = Optional.of(Objects.requireNonNull(duration, "duration"));
            return this;
        }

        public Builder noMatchDuration() {
            this.matchDuration = Optional.empty();
            return this;
        }

        public Builder secondModuleFactory(ScoreboardModuleFactory factory) {
            this.secondModuleFactory = Optional.of(Objects.requireNonNull(factory, "factory"));
            return this;
        }

        public Builder addBetweenModule(ScoreboardModuleFactory factory) {
            this.betweenModuleFactories.add(Objects.requireNonNull(factory, "factory"));
            return this;
        }

        public DuelsScoreboardLayout build() {
            return new DuelsScoreboardLayout(this);
        }
    }
}
