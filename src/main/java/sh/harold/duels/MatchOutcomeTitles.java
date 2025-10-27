package sh.harold.duels;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import sh.harold.fulcrum.minigame.state.context.StateContext;
import sh.harold.fulcrum.minigame.team.MatchTeam;

public final class MatchOutcomeTitles {
    private static final Title.Times MATCH_DECISION_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1));

    private MatchOutcomeTitles() {
    }

    public static void showOutcome(StateContext context, MatchTeam winningTeam) {
        Set<UUID> winners = new HashSet<>(winningTeam.members());

        Component subtitle = buildSubtitle(context, winningTeam);
        Title victoryTitle = createVictoryTitle(subtitle);
        Title defeatTitle = createDefeatTitle(subtitle);

        context.teams().teams().forEach(team -> team.members().forEach(memberId ->
                context.findPlayer(memberId).ifPresent(player -> {
                    if (winners.contains(memberId)) {
                        player.showTitle(victoryTitle);
                    } else {
                        player.showTitle(defeatTitle);
                    }
                })));
    }

    public static void showTie(StateContext context) {
        Title tieTitle = createTieTitle();
        context.teams().teams()
                .forEach(team -> team.members()
                        .forEach(memberId -> context.findPlayer(memberId)
                                .ifPresent(player -> player.showTitle(tieTitle))));
    }

    private static Component buildSubtitle(StateContext context, MatchTeam winningTeam) {
        Component winnerComponent = winningTeam.members().stream()
                .map(context::findPlayer)
                .flatMap(Optional::stream)
                .findFirst()
                .map(MatchOutcomeTitles::buildPlayerComponent)
                .orElse(Component.text(winningTeam.getId(), NamedTextColor.WHITE));

        return Component.text()
                .append(winnerComponent)
                .append(Component.text(" won the Duel!", NamedTextColor.WHITE))
                .build();
    }

    private static Component buildPlayerComponent(Player player) {
        TextColor nameColor = resolveNameColor(player);
        return Component.text(player.getName()).color(nameColor);
    }

    private static TextColor resolveNameColor(Player player) {
        Team team = player.getScoreboard().getPlayerTeam(player);
        if (team != null && team.color() != null) {
            return team.color();
        }

        Component listName = player.playerListName();
        if (listName != null && listName.color() != null) {
            return listName.color();
        }

        Component displayName = player.displayName();
        if (displayName != null && displayName.color() != null) {
            return displayName.color();
        }

        return NamedTextColor.WHITE;
    }

    private static Title createVictoryTitle(Component subtitle) {
        Component title = Component.text("VICTORY!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        return Title.title(title, subtitle, MATCH_DECISION_TIMES);
    }

    private static Title createDefeatTitle(Component subtitle) {
        Component title = Component.text("DEFEAT", NamedTextColor.RED).decorate(TextDecoration.BOLD);
        return Title.title(title, subtitle, MATCH_DECISION_TIMES);
    }

    private static Title createTieTitle() {
        Component title = Component.text("TIE", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
        Component subtitle = Component.text("It's a tie!", NamedTextColor.WHITE);
        return Title.title(title, subtitle, MATCH_DECISION_TIMES);
    }
}
