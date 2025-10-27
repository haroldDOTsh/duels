package sh.harold.duels.sumo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class SumoSpawnLayout {
    private final Map<String, SpawnPoint> labeledSpawns;
    private final List<SpawnPoint> orderedFallback;

    private SumoSpawnLayout(Map<String, SpawnPoint> labeledSpawns, List<SpawnPoint> orderedFallback) {
        this.labeledSpawns = labeledSpawns;
        this.orderedFallback = orderedFallback;
    }

    static SumoSpawnLayout from(Map<Location, JsonObject> pois) {
        Map<String, SpawnPoint> labeled = new LinkedHashMap<>();
        List<SpawnPoint> fallback = new ArrayList<>();

        pois.forEach((location, config) -> {
            if (!config.has("type")) {
                return;
            }
            String type = config.get("type").getAsString();
            if (!SumoRules.SPAWN_POI_TYPE.equalsIgnoreCase(type)) {
                return;
            }
            Location spawnLocation = cloneAndNormalize(location);
            if (config.has("yaw")) {
                spawnLocation.setYaw(parseFloat(config.get("yaw")));
            }
            if (config.has("pitch")) {
                spawnLocation.setPitch(parseFloat(config.get("pitch")));
            }

            String label = null;
            if (config.has("team")) {
                label = config.get("team").getAsString();
            } else if (config.has("id")) {
                label = config.get("id").getAsString();
            }

            SpawnPoint point = new SpawnPoint(label == null ? null : label.toLowerCase(Locale.ROOT), spawnLocation);
            fallback.add(point);
            if (point.label() != null) {
                labeled.put(point.label(), point);
            }
        });

        return new SumoSpawnLayout(labeled, fallback);
    }

    Location resolve(String teamId, int fallbackIndex) {
        String key = teamId.toLowerCase(Locale.ROOT);
        SpawnPoint labeledPoint = labeledSpawns.get(key);
        if (labeledPoint != null) {
            return labeledPoint.location().clone();
        }
        if (fallbackIndex >= 0 && fallbackIndex < orderedFallback.size()) {
            return orderedFallback.get(fallbackIndex).location().clone();
        }
        throw new IllegalStateException("Missing Sumo spawn for team " + teamId);
    }

    private static Location cloneAndNormalize(Location source) {
        Objects.requireNonNull(source.getWorld(), "Spawn POI missing world");
        Location clone = source.clone();
        // Center players on the block if integer coordinates are provided.
        if (Math.abs(clone.getX() - clone.getBlockX()) < 0.0001) {
            clone.setX(clone.getBlockX() + 0.5);
        }
        if (Math.abs(clone.getZ() - clone.getBlockZ()) < 0.0001) {
            clone.setZ(clone.getBlockZ() + 0.5);
        }
        return clone;
    }

    private static float parseFloat(JsonElement element) {
        try {
            return element.getAsFloat();
        } catch (NumberFormatException ex) {
            return 0F;
        }
    }

    record SpawnPoint(String label, Location location) {
    }
}
