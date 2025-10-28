package sh.harold.duels.scoreboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.api.lifecycle.ServerIdentifier;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardBuilder;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardService;
import sh.harold.fulcrum.api.message.scoreboard.module.DynamicContentProvider;
import sh.harold.fulcrum.api.message.scoreboard.module.ScoreboardModule;
import sh.harold.fulcrum.api.message.scoreboard.module.StaticContentProvider;
import sh.harold.fulcrum.api.message.scoreboard.registry.ScoreboardDefinition;
import sh.harold.fulcrum.api.rank.Rank;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.minigame.match.RosterManager;
import sh.harold.fulcrum.minigame.match.RosterManager.PlayerState;
import sh.harold.fulcrum.minigame.state.context.StateContext;
import sh.harold.fulcrum.minigame.team.MatchTeam;
import sh.harold.fulcrum.minigame.MinigameAttributes;

public final class DuelsScoreboard {
    private static final String SCOREBOARD_ATTRIBUTE = "duels.inGame.scoreboard";
    private static final long FAST_REFRESH_INTERVAL_MILLIS = 500L;
    private static final long STANDARD_REFRESH_INTERVAL_MILLIS = Duration.ofSeconds(1L).toMillis();
    private static final long REFRESH_PERIOD_TICKS = 10L;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();
    private static final char[] DIRECTION_ARROWS = {'↑', '↗', '→', '↘', '↓', '↙', '←', '↖'};

    private DuelsScoreboard() {
    }

    public static void apply(StateContext context, DuelsScoreboardLayout layout) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(layout, "layout");

        ScoreboardService service = resolveService();
        if (service == null || hasState(context)) {
            return;
        }

        String scoreboardId = buildScoreboardId(context.getMatchId());
        if (service.isScoreboardRegistered(scoreboardId)) {
            service.unregisterScoreboard(scoreboardId);
        }
        DuelsMatchClock clock = layout.matchDuration()
                .map(DuelsMatchClock::start)
                .orElseGet(DuelsMatchClock::infinite);

        String title = layout.title()
                .filter(value -> !value.isBlank())
                .orElseGet(() -> "&e&l" + layout.familyDisplayName().toUpperCase(Locale.ROOT));
        ScoreboardBuilder builder = new ScoreboardBuilder(scoreboardId).title(title);
        String headerLabel = layout.headerLabel()
                .filter(label -> !label.isBlank())
                .orElseGet(() -> resolveHeaderLabel(context));
        if (!headerLabel.isBlank()) {
            builder.headerLabel(headerLabel);
        }

        builder.module(new TimeLeftModule(clock));

        ScoreboardModule secondModule = layout.secondModuleFactory()
                .map(factory -> factory.create(context, clock))
                .orElseGet(() -> new OpponentModule(context));
        builder.module(secondModule);

        for (DuelsScoreboardLayout.ScoreboardModuleFactory factory : layout.betweenModuleFactories()) {
            builder.module(factory.create(context, clock));
        }

        builder.module(new MetaModule(layout));

        ScoreboardDefinition definition = builder.build();
        service.registerScoreboard(scoreboardId, definition);

        BukkitTask refreshTask = context.scheduleRepeatingTask(() -> refresh(context),
                REFRESH_PERIOD_TICKS, REFRESH_PERIOD_TICKS);
        DuelsScoreboardState state = new DuelsScoreboardState(scoreboardId, clock, layout, refreshTask);
        context.setAttribute(SCOREBOARD_ATTRIBUTE, state);
        state.clock().setFrozen(context.isStateMachineFrozen());

        context.forEachPlayer(player -> {
            UUID playerId = player.getUniqueId();
            service.showScoreboard(playerId, scoreboardId);
            service.refreshPlayerScoreboard(playerId);
        });
        refresh(context);
    }

    public static void showToPlayer(StateContext context, UUID playerId) {
        if (context == null || playerId == null) {
            return;
        }
        ScoreboardService service = resolveService();
        Optional<DuelsScoreboardState> state = resolveState(context);
        if (service == null || state.isEmpty()) {
            return;
        }
        service.showScoreboard(playerId, state.get().scoreboardId());
        service.refreshPlayerScoreboard(playerId);
    }

    public static void hideFromPlayer(StateContext context, UUID playerId) {
        if (context == null || playerId == null) {
            return;
        }
        ScoreboardService service = resolveService();
        if (service == null) {
            return;
        }
        service.hideScoreboard(playerId);
    }

    public static void refresh(StateContext context) {
        if (context == null) {
            return;
        }
        Optional<DuelsScoreboardState> stateOpt = resolveState(context);
        if (stateOpt.isEmpty()) {
            return;
        }
        DuelsScoreboardState state = stateOpt.get();
        state.clock().setFrozen(context.isStateMachineFrozen());
        ScoreboardService service = resolveService();
        if (service == null) {
            return;
        }
        context.forEachPlayer(player -> service.refreshPlayerScoreboard(player.getUniqueId()));
    }

    public static void teardown(StateContext context) {
        if (context == null) {
            return;
        }
        ScoreboardService service = resolveService();
        Optional<DuelsScoreboardState> state = resolveState(context);
        if (state.isEmpty()) {
            return;
        }
        DuelsScoreboardState scoreboardState = state.get();
        cancelRefreshTask(scoreboardState);
        if (service != null) {
            context.forEachPlayer(player -> service.hideScoreboard(player.getUniqueId()));
            if (service.isScoreboardRegistered(scoreboardState.scoreboardId())) {
                service.unregisterScoreboard(scoreboardState.scoreboardId());
            }
        }
        context.removeAttribute(SCOREBOARD_ATTRIBUTE);
    }

    public static Optional<DuelsMatchClock> getClock(StateContext context) {
        return resolveState(context).map(DuelsScoreboardState::clock);
    }

    private static boolean hasState(StateContext context) {
        return resolveState(context).isPresent();
    }

    private static Optional<DuelsScoreboardState> resolveState(StateContext context) {
        return context == null ? Optional.empty()
                : context.getAttributeOptional(SCOREBOARD_ATTRIBUTE, DuelsScoreboardState.class);
    }

    private static void cancelRefreshTask(DuelsScoreboardState state) {
        if (state.refreshTask() != null) {
            state.refreshTask().cancel();
        }
    }

    private static ScoreboardService resolveService() {
        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator == null) {
            return null;
        }
        return locator.findService(ScoreboardService.class).orElse(null);
    }

    private static String buildScoreboardId(UUID matchId) {
        return "duels:match/" + matchId;
    }

    private static String resolveHeaderLabel(StateContext context) {
        Map<String, String> metadata = readSlotMetadata(context);
        String slotId = context.getAttributeOptional(MinigameAttributes.SLOT_ID, String.class)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .orElseGet(() -> firstNonBlank(
                        metadata.get("slotId"),
                        metadata.get("slot"),
                        metadata.get("slotName")));

        if (slotId.isEmpty()) {
            throw new IllegalStateException("Missing slot identifier for duels scoreboard header");
        }

        return "&8" + slotId;
    }

    private static Map<String, String> readSlotMetadata(StateContext context) {
        return context.getAttributeOptional(MinigameAttributes.SLOT_METADATA, Map.class)
                .map(raw -> {
                    Map<String, String> metadata = new HashMap<>();
                    ((Map<?, ?>) raw).forEach((key, value) -> {
                        if (key == null || value == null) {
                            return;
                        }
                        String stringKey = String.valueOf(key);
                        String stringValue = String.valueOf(value).trim();
                        if (!stringKey.isBlank() && !stringValue.isBlank()) {
                            metadata.put(stringKey, stringValue);
                        }
                    });
                    return metadata;
                })
                .orElseGet(Map::of);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "";
    }

    private record DuelsScoreboardState(String scoreboardId, DuelsMatchClock clock,
                                        DuelsScoreboardLayout layout, BukkitTask refreshTask) {
    }

    private static final class TimeLeftModule implements ScoreboardModule {
        private final DuelsMatchClock clock;
        private final DynamicContentProvider provider;

        private TimeLeftModule(DuelsMatchClock clock) {
            this.clock = clock;
            this.provider = new DynamicContentProvider(this::buildLines, STANDARD_REFRESH_INTERVAL_MILLIS);
        }

        @Override
        public String getModuleId() {
            return "duels_time";
        }

        @Override
        public DynamicContentProvider getContentProvider() {
            return provider;
        }

        private List<String> buildLines(UUID ignored) {
            List<String> lines = new ArrayList<>(1);
            lines.add("&fTime Left: &a" + clock.formatRemaining());
            return lines;
        }
    }

    private static final class OpponentModule implements ScoreboardModule {
        private final StateContext context;
        private final DynamicContentProvider provider;

        private OpponentModule(StateContext context) {
            this.context = context;
            this.provider = new DynamicContentProvider(this::buildLines, FAST_REFRESH_INTERVAL_MILLIS);
        }

        @Override
        public String getModuleId() {
            return "duels_opponent";
        }

        @Override
        public DynamicContentProvider getContentProvider() {
            return provider;
        }

        private List<String> buildLines(UUID viewerId) {
            List<String> lines = new ArrayList<>(2);
            lines.add("&f&lOpponent:");
            Optional<Player> viewerOpt = context.findPlayer(viewerId);
            if (viewerOpt.isEmpty()) {
                lines.add("&7Unavailable");
                return lines;
            }

            Player viewer = viewerOpt.get();
            String viewerTeamId = context.team(viewerId)
                    .map(MatchTeam::getId)
                    .orElse(null);
            Player opponent = findPrimaryOpponent(viewer, viewerTeamId);
            if (opponent == null) {
                lines.add("&7Searching...");
                return lines;
            }

            String arrow = directionArrow(viewer.getLocation(), opponent.getLocation());
            String name = resolveColoredName(opponent);
            String health = formatHealth(opponent);
            lines.add("&7" + arrow + " " + name + " " + health);
            return lines;
        }

        private Player findPrimaryOpponent(Player viewer, String viewerTeamId) {
            double nearestDistance = Double.MAX_VALUE;
            Player nearest = null;

            for (MatchTeam team : context.teams().teams()) {
                if (viewerTeamId != null && viewerTeamId.equals(team.getId())) {
                    continue;
                }
                for (UUID memberId : team.members()) {
                    if (!isActive(memberId)) {
                        continue;
                    }
                    Player opponent = context.findPlayer(memberId).orElse(null);
                    if (opponent == null) {
                        continue;
                    }
                    double distance = viewer.getLocation().distanceSquared(opponent.getLocation());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = opponent;
                    }
                }
            }
            return nearest;
        }

        private boolean isActive(UUID playerId) {
            RosterManager.Entry entry = context.roster().get(playerId);
            return entry != null && entry.getState() == PlayerState.ACTIVE;
        }

        private String resolveColoredName(Player player) {
            Rank rank = RankUtils.getEffectiveRank(player);
            NamedTextColor color = rank != null && rank.getNameColor() != null
                    ? rank.getNameColor()
                    : NamedTextColor.WHITE;
            Component component = Component.text(player.getName(), color);
            String legacy = LEGACY_SERIALIZER.serialize(component);
            return legacy.replace('\u00A7', '&');
        }

        private String formatHealth(Player player) {
            double maxHealth = player.getMaxHealth();
            double health = Math.max(0D, Math.min(player.getHealth(), maxHealth));
            int hearts = (int) Math.ceil(health / 2D);
            hearts = Math.max(0, hearts);
            return "&c" + hearts + "❤";
        }

        private String directionArrow(Location source, Location target) {
            Vector delta = target.toVector().subtract(source.toVector());
            if (delta.lengthSquared() < 0.01D) {
                return "•";
            }
            double yawRadians = Math.toRadians(source.getYaw());
            double angleToTarget = Math.atan2(-delta.getX(), delta.getZ());
            double relative = (angleToTarget - yawRadians + (Math.PI * 2)) % (Math.PI * 2);
            int index = (int) Math.round(relative / (Math.PI / 4)) & 7;
            return String.valueOf(DIRECTION_ARROWS[index]);
        }
    }

    private static final class MetaModule implements ScoreboardModule {
        private final StaticContentProvider provider;

        private MetaModule(DuelsScoreboardLayout layout) {
            this.provider = new StaticContentProvider(buildLines(layout));
        }

        @Override
        public String getModuleId() {
            return "duels_meta";
        }

        @Override
        public StaticContentProvider getContentProvider() {
            return provider;
        }

        private List<String> buildLines(DuelsScoreboardLayout layout) {
            return List.of(
                    "&fMode: &a" + layout.modeLabel(),
                    "&fDaily Streak: &a--",
                    "&fBest Daily Streak: &a--"
            );
        }
    }
}
