package sh.harold.duels;

import java.util.Collection;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.duels.sumo.SumoMinigame;
import sh.harold.fulcrum.api.slot.SlotFamilyDescriptor;
import sh.harold.fulcrum.minigame.MinigameEngine;
import sh.harold.fulcrum.minigame.MinigameModule;
import sh.harold.fulcrum.minigame.MinigameRegistration;

public final class DuelsPlugin extends JavaPlugin implements MinigameModule {
    public static final String MODULE_ID = "duels";
    public static final String MODULE_DESCRIPTION = "Multi-variant duels module";

    private SlotFamilyDescriptor sumoFamily;

    @Override
    public void onEnable() {
        getLogger().info("Duels module loaded");
    }

    @Override
    public void onDisable() {
        getLogger().info("Duels module disabled");
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }

    @Override
    public Collection<SlotFamilyDescriptor> getSlotFamilies() {
        return List.of(getSumoFamily());
    }

    @Override
    public Collection<MinigameRegistration> registerMinigames(MinigameEngine engine) {
        return List.of(SumoMinigame.createRegistration(this, getSumoFamily()));
    }

    private SlotFamilyDescriptor getSumoFamily() {
        if (sumoFamily == null) {
            sumoFamily = SumoMinigame.createDescriptor();
        }
        return sumoFamily;
    }
}
