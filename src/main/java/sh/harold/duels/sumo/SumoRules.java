package sh.harold.duels.sumo;

import java.time.Duration;

final class SumoRules {
    static final double TIE_THRESHOLD = 2.0D;
    static final double LOSS_THRESHOLD = 8.0D;
    static final String SPAWN_POI_TYPE = "sumo_spawn";
    static final Duration MATCH_DURATION = Duration.ofMinutes(3);

    private SumoRules() {
    }
}
