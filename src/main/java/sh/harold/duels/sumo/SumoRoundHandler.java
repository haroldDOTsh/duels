package sh.harold.duels.sumo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import sh.harold.duels.MatchOutcomeTitles;
import sh.harold.duels.scoreboard.DuelsScoreboard;
import sh.harold.duels.scoreboard.DuelsScoreboardLayout;
import sh.harold.fulcrum.api.world.poi.POIRegistry;
import sh.harold.fulcrum.fundamentals.actionflag.ActionFlagContexts;
import sh.harold.fulcrum.fundamentals.actionflag.ActionFlagService;
import sh.harold.fulcrum.fundamentals.actionflag.ActionFlag;
import sh.harold.fulcrum.fundamentals.actionflag.FlagBundle;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.minigame.MinigameAttributes;
import sh.harold.fulcrum.minigame.MinigameBlueprint;
import sh.harold.fulcrum.minigame.defaults.InGameHandler;
import sh.harold.fulcrum.minigame.environment.MinigameEnvironmentService;
import sh.harold.fulcrum.minigame.match.RosterManager;
import sh.harold.fulcrum.minigame.team.MatchTeam;
import sh.harold.fulcrum.minigame.state.context.StateContext;
import com.google.gson.JsonObject;

final class SumoRoundHandler implements InGameHandler {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();
    private static final String SUMO_ACTIVE_FLAG_CONTEXT = "duels:sumo/active";
    private static final AtomicBoolean FLAG_CONTEXT_REGISTERED = new AtomicBoolean(false);

    private final JavaPlugin plugin;
    private final POIRegistry poiRegistry;

    SumoRoundHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.poiRegistry = ServiceLocatorImpl.getInstance()
                .findService(POIRegistry.class)
                .orElseThrow(() -> new IllegalStateException("POIRegistry service unavailable"));
    }

    @Override
    public void onMatchStart(StateContext context) {
        MinigameEnvironmentService.MatchEnvironment environment = context
                .getAttributeOptional(MinigameAttributes.MATCH_ENVIRONMENT,
                        MinigameEnvironmentService.MatchEnvironment.class)
                .orElseThrow(() -> new IllegalStateException("Missing match environment for Sumo"));

        Map<String, String> slotMetadata = readSlotMetadata(context);
        SumoVariant variant = SumoVariant.fromMetadata(slotMetadata);

        Map<Location, JsonObject> worldPois = poiRegistry.getWorldPOIs(environment.worldName());
        SumoSpawnLayout spawnLayout = SumoSpawnLayout.from(worldPois);

        List<MatchTeam> teams = context.teams().teams().stream()
                .sorted(Comparator.comparing(MatchTeam::getId))
                .collect(Collectors.toList());
        if (teams.size() < 2) {
            throw new IllegalStateException("Sumo requires exactly two teams");
        }

        Map<String, Location> teamSpawns = assignSpawns(teams, spawnLayout);
        SumoMatchContext matchContext = new SumoMatchContext(variant, environment.matchSpawn().getY(), teamSpawns);
        context.setAttribute(SumoAttributes.MATCH_CONTEXT, matchContext);

        String actionFlagContext = resolveActiveFlagContext();
        context.applyFlagContext(actionFlagContext);
        preparePlayers(context, teams, teamSpawns);

        DuelsScoreboardLayout scoreboardLayout = DuelsScoreboardLayout.builder("Sumo", matchContext.variant().scoreboardLabel())
                .matchDuration(SumoRules.MATCH_DURATION)
                .build();
        DuelsScoreboard.apply(context, scoreboardLayout);

        context.broadcast(ChatColor.GOLD + "Sumo " + variant.displayName()
                + ChatColor.AQUA + " - first team to fall loses!");
    }

    @Override
    public void onTick(StateContext context) {
        SumoMatchContext matchContext = context.getAttributeOptional(
                        SumoAttributes.MATCH_CONTEXT, SumoMatchContext.class)
                .orElse(null);
        if (matchContext == null || matchContext.resultDeclared()) {
            return;
        }

        boolean timedOut = DuelsScoreboard.getClock(context)
                .map(clock -> !clock.isInfinite() && clock.isExpired())
                .orElse(false);
        if (timedOut) {
            if (!matchContext.tieLatched()) {
                matchContext.latchTie();
            }
            resolveOutcome(context, matchContext);
            return;
        }

        latchTieIfNeeded(context, matchContext);

        List<UUID> fallenPlayers = detectFalls(context, matchContext);
        if (fallenPlayers.isEmpty()) {
            return;
        }

        eliminatePlayers(context, fallenPlayers);
        resolveOutcome(context, matchContext);
    }

    @Override
    public void onMatchEnd(StateContext context) {
        clearHealthGuards(context);
        DuelsScoreboard.teardown(context);
        context.removeAttribute(SumoAttributes.MATCH_CONTEXT);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readSlotMetadata(StateContext context) {
        return context.getAttributeOptional(MinigameAttributes.SLOT_METADATA, Map.class)
                .map(raw -> {
                    Map<String, String> metadata = new HashMap<>();
                    ((Map<?, ?>) raw).forEach((key, value) -> {
                        if (key != null && value != null) {
                            metadata.put(String.valueOf(key), String.valueOf(value));
                        }
                    });
                    return metadata;
                })
                .orElseGet(Map::of);
    }

    private Map<String, Location> assignSpawns(List<MatchTeam> teams, SumoSpawnLayout layout) {
        Map<String, Location> assignments = new HashMap<>();
        for (int index = 0; index < teams.size(); index++) {
            MatchTeam team = teams.get(index);
            Location spawn = layout.resolve(team.getId(), index);
            assignments.put(team.getId().toLowerCase(Locale.ROOT), spawn);
        }
        return assignments;
    }

    private void preparePlayers(StateContext context, Collection<MatchTeam> teams,
                                Map<String, Location> teamSpawns) {
        for (MatchTeam team : teams) {
            String teamKey = team.getId().toLowerCase(Locale.ROOT);
            Location spawn = teamSpawns.get(teamKey);
            if (spawn == null) {
                plugin.getLogger().warning("Missing spawn for team " + team.getId());
                continue;
            }
            Location opposingSpawn = findOpposingSpawn(teamKey, teamSpawns);
            List<Location> slots = distributeAround(spawn, team.members().size());
            orientSlots(slots, spawn, opposingSpawn);
            int slotIndex = 0;
            for (UUID memberId : team.members()) {
                int index = Math.min(slotIndex, slots.size() - 1);
                Location target = slots.get(index);
                context.findPlayer(memberId).ifPresent(player -> preparePlayer(player, target));
                slotIndex++;
            }
        }
    }

    private List<Location> distributeAround(Location center, int members) {
        if (members <= 1) {
            return List.of(center.clone());
        }
        List<Location> positions = new ArrayList<>(members);
        double radius = 0.75;
        for (int i = 0; i < members; i++) {
            double angle = (2 * Math.PI * i) / members;
            Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            point.setYaw(center.getYaw());
            point.setPitch(center.getPitch());
            positions.add(point);
        }
        return positions;
    }

    private void preparePlayer(Player player, Location spawn) {
        player.teleport(spawn, TeleportCause.PLUGIN);
        player.setVelocity(new Vector());
        player.setFallDistance(0F);
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0F);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setAllowFlight(false);
        player.setFlying(false);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double targetHealth = maxHealth != null ? maxHealth.getValue() : player.getMaxHealth();
        player.setHealth(Math.max(1D, targetHealth));
        applyHealthGuard(player);
    }

    private Location findOpposingSpawn(String teamId, Map<String, Location> teamSpawns) {
        String key = teamId.toLowerCase(Locale.ROOT);
        return teamSpawns.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private void orientSlots(List<Location> slots, Location source, Location opposingSpawn) {
        if (opposingSpawn == null) {
            return;
        }
        float yaw = computeFacingYaw(source, opposingSpawn);
        for (Location slot : slots) {
            slot.setYaw(yaw);
            slot.setPitch(0F);
        }
    }

    private float computeFacingYaw(Location from, Location target) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
    }

    private void applyHealthGuard(Player player) {
        player.removeMetadata(SumoAttributes.HEALTH_GUARD_METADATA, plugin);
        player.setMetadata(SumoAttributes.HEALTH_GUARD_METADATA, new FixedMetadataValue(plugin, Boolean.TRUE));
    }

    private void clearHealthGuard(Player player) {
        player.removeMetadata(SumoAttributes.HEALTH_GUARD_METADATA, plugin);
    }

    private void clearHealthGuards(StateContext context) {
        for (MatchTeam team : context.teams().teams()) {
            for (UUID memberId : team.members()) {
                context.findPlayer(memberId).ifPresent(this::clearHealthGuard);
            }
        }
    }

    private void latchTieIfNeeded(StateContext context, SumoMatchContext matchContext) {
        if (matchContext.tieLatched()) {
            return;
        }
        Set<String> teamsBelowThreshold = new HashSet<>();
        for (UUID playerId : context.getActivePlayers()) {
            Optional<Player> player = context.findPlayer(playerId);
            if (player.isEmpty()) {
                continue;
            }
            if (player.get().getLocation().getY() < matchContext.tieY()) {
                context.team(playerId)
                        .map(MatchTeam::getId)
                        .ifPresent(id -> teamsBelowThreshold.add(id.toLowerCase(Locale.ROOT)));
            }
        }
        if (teamsBelowThreshold.size() >= 2) {
            matchContext.latchTie();
        }
    }

    private List<UUID> detectFalls(StateContext context, SumoMatchContext matchContext) {
        List<UUID> fallen = new ArrayList<>();
        for (UUID playerId : context.getActivePlayers()) {
            Optional<Player> player = context.findPlayer(playerId);
            if (player.isEmpty()) {
                continue;
            }
            if (player.get().getLocation().getY() < matchContext.eliminationY()) {
                fallen.add(playerId);
            }
        }
        return fallen;
    }

    private void eliminatePlayers(StateContext context, Collection<UUID> players) {
        for (UUID playerId : players) {
            context.eliminatePlayer(playerId, false, 0L);
            context.transitionPlayerToSpectator(playerId);
            context.findPlayer(playerId).ifPresent(this::clearHealthGuard);
        }
    }

    private void resolveOutcome(StateContext context, SumoMatchContext matchContext) {
        if (matchContext.resultDeclared()) {
            return;
        }
        if (matchContext.tieLatched()) {
            announceTie(context, matchContext);
            return;
        }

        List<MatchTeam> aliveTeams = context.teams().teams().stream()
                .filter(team -> team.members().stream().anyMatch(member -> isActive(context, member)))
                .collect(Collectors.toList());

        if (aliveTeams.size() == 1) {
            announceWinner(context, matchContext, aliveTeams.get(0));
        } else if (aliveTeams.isEmpty()) {
            announceTie(context, matchContext);
        }
    }

    private boolean isActive(StateContext context, UUID playerId) {
        RosterManager.Entry entry = context.roster().get(playerId);
        return entry != null && entry.getState() == RosterManager.PlayerState.ACTIVE;
    }

    private void announceWinner(StateContext context, SumoMatchContext matchContext, MatchTeam winner) {
        matchContext.markResultDeclared();
        MatchOutcomeTitles.showOutcome(context, winner);
        String teamName = LEGACY_SERIALIZER.serialize(winner.getDisplayName());
        context.broadcast(ChatColor.GREEN + matchContext.variant().displayName()
                + ChatColor.GRAY + " winner: " + ChatColor.AQUA + teamName);
        concludeMatch(context);
    }

    private void announceTie(StateContext context, SumoMatchContext matchContext) {
        matchContext.markResultDeclared();
        MatchOutcomeTitles.showTie(context);
        context.broadcast(ChatColor.YELLOW + matchContext.variant().displayName()
                + ChatColor.GRAY + " tie! Both teams left the platform.");
        concludeMatch(context);
    }

    private void concludeMatch(StateContext context) {
        clearHealthGuards(context);
        context.setAttribute(MinigameAttributes.MATCH_COMPLETE, Boolean.TRUE);
        context.markMatchComplete();
        context.requestTransition(MinigameBlueprint.STATE_END_GAME);
        DuelsScoreboard.teardown(context);
    }

    private String resolveActiveFlagContext() {
        if (FLAG_CONTEXT_REGISTERED.get()) {
            return SUMO_ACTIVE_FLAG_CONTEXT;
        }
        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator == null) {
            plugin.getLogger().warning("Service locator unavailable; using match fallback action flags.");
            return ActionFlagContexts.MATCH_ACTIVE_FALLBACK;
        }
        Optional<ActionFlagService> serviceOpt = locator.findService(ActionFlagService.class);
        if (serviceOpt.isEmpty()) {
            plugin.getLogger().warning("ActionFlagService unavailable; using match fallback action flags.");
            return ActionFlagContexts.MATCH_ACTIVE_FALLBACK;
        }
        ActionFlagService service = serviceOpt.get();
        if (!service.hasContext(SUMO_ACTIVE_FLAG_CONTEXT)) {
            EnumSet<ActionFlag> flags = EnumSet.allOf(ActionFlag.class);
            flags.remove(ActionFlag.INVISIBLE_PACKET);
            flags.remove(ActionFlag.INVISIBLE_POTION);
            FlagBundle bundle = FlagBundle.of(SUMO_ACTIVE_FLAG_CONTEXT, flags)
                    .withGamemode(GameMode.SURVIVAL);
            service.registerContext(SUMO_ACTIVE_FLAG_CONTEXT, bundle);
        }
        FLAG_CONTEXT_REGISTERED.set(true);
        return SUMO_ACTIVE_FLAG_CONTEXT;
    }
}
