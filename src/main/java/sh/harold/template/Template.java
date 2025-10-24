package sh.harold.template;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.module.FulcrumModule;
import sh.harold.fulcrum.api.module.ModuleInfo;

@ModuleInfo(name = Template.MODULE_ID, description = Template.MODULE_DESCRIPTION) // Update constants to match your module metadata
public final class Template extends JavaPlugin implements FulcrumModule {
    public static final String MODULE_ID = "template"; // Replace with your Fulcrum module ID
    public static final String MODULE_DESCRIPTION = "Template module for Fulcrum integrations"; // Replace with your module description

    @Override
    public void onEnable() {
        // Plugin startup logic
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
