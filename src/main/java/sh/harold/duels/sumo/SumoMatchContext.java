package sh.harold.duels.sumo;

import java.util.Collections;
import java.util.Map;
import org.bukkit.Location;

final class SumoMatchContext {
    private final SumoVariant variant;
    private final double originY;
    private final Map<String, Location> teamSpawns;
    private boolean tieLatched;
    private boolean resultDeclared;

    SumoMatchContext(SumoVariant variant, double originY, Map<String, Location> teamSpawns) {
        this.variant = variant;
        this.originY = originY;
        this.teamSpawns = Collections.unmodifiableMap(teamSpawns);
    }

    public SumoVariant variant() {
        return variant;
    }

    public double originY() {
        return originY;
    }

    public double eliminationY() {
        return originY - SumoRules.LOSS_THRESHOLD;
    }

    public double tieY() {
        return originY - SumoRules.TIE_THRESHOLD;
    }

    public Map<String, Location> teamSpawns() {
        return teamSpawns;
    }

    public boolean tieLatched() {
        return tieLatched;
    }

    public void latchTie() {
        this.tieLatched = true;
    }

    public boolean resultDeclared() {
        return resultDeclared;
    }

    public void markResultDeclared() {
        this.resultDeclared = true;
    }
}
