package sh.harold.duels.sumo;

import java.util.Locale;
import java.util.Map;

enum SumoVariant {
    SOLO("solo", "Solo 1v1", "Solo Duel", 1, 2),
    DUOS("duos", "Duos 2v2", "Duos 2v2", 2, 4);

    private static final String VARIANT_METADATA_KEY = "variant";

    private final String id;
    private final String displayName;
    private final String scoreboardLabel;
    private final int playersPerTeam;
    private final int minimumPlayers;

    SumoVariant(String id, String displayName, String scoreboardLabel, int playersPerTeam, int minimumPlayers) {
        this.id = id;
        this.displayName = displayName;
        this.scoreboardLabel = scoreboardLabel;
        this.playersPerTeam = playersPerTeam;
        this.minimumPlayers = minimumPlayers;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String scoreboardLabel() {
        return scoreboardLabel;
    }

    public int playersPerTeam() {
        return playersPerTeam;
    }

    public int minimumPlayers() {
        return minimumPlayers;
    }

    public static SumoVariant fromMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return SOLO;
        }
        String variantId = metadata.get(VARIANT_METADATA_KEY);
        if (variantId == null || variantId.isBlank()) {
            return SOLO;
        }
        String normalized = variantId.trim().toLowerCase(Locale.ROOT);
        for (SumoVariant variant : values()) {
            if (variant.id.equalsIgnoreCase(normalized)) {
                return variant;
            }
        }
        return SOLO;
    }
}
