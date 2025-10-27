package sh.harold.duels.sumo;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.slot.SlotFamilyDescriptor;
import sh.harold.fulcrum.minigame.MinigameAttributes;
import sh.harold.fulcrum.minigame.MinigameBlueprint;
import sh.harold.fulcrum.minigame.MinigameRegistration;

public final class SumoMinigame {
    public static final String FAMILY_ID = "sumo";
    private static final String DEFAULT_MAP_POOL = "sumo";
    private static final String DEFAULT_VARIANT = SumoVariant.SOLO.id();
    private static final String ADVERTISED_VARIANTS = Arrays.stream(SumoVariant.values())
            .map(SumoVariant::id)
            .collect(Collectors.joining(","));
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 2;
    private static final int PLAYER_EQUIVALENT = 10;
    private static final String TEAM_COUNT = "2";
    private static final String TEAM_MAX = "1";
    private static final String PRE_LOBBY_SCHEMATIC = "duels_prelobby";

    private SumoMinigame() {
    }

    public static SlotFamilyDescriptor createDescriptor() {
        return SlotFamilyDescriptor.builder(FAMILY_ID, MIN_PLAYERS, MAX_PLAYERS)
                .playerEquivalentFactor(PLAYER_EQUIVALENT)
                .putMetadata("mapPool", DEFAULT_MAP_POOL)
                .putMetadata("team.count", TEAM_COUNT)
                .putMetadata("team.max", TEAM_MAX)
                .putMetadata("variant", DEFAULT_VARIANT)
                .putMetadata("defaultVariant", DEFAULT_VARIANT)
                .putMetadata("variants", ADVERTISED_VARIANTS)
                .putMetadata("preLobbySchematic", PRE_LOBBY_SCHEMATIC)
                .build();
    }

    public static MinigameRegistration createRegistration(JavaPlugin plugin, SlotFamilyDescriptor descriptor) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(descriptor, "descriptor");

        MinigameBlueprint blueprint = MinigameBlueprint.standard()
                .preLobby(builder -> builder.countdownSeconds(10).minimumPlayers(MIN_PLAYERS))
                .inGame(new SumoRoundHandler(plugin))
                .onInGameComplete(context -> context.getAttributeOptional(
                                MinigameAttributes.MATCH_COMPLETE, Boolean.class)
                        .orElse(false))
                .build();

        return new MinigameRegistration(FAMILY_ID, descriptor, blueprint);
    }
}
