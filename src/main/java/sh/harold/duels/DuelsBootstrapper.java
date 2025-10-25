package sh.harold.duels;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.module.BootstrapContextHolder;
import sh.harold.fulcrum.api.module.FulcrumEnvironment;

/**
 * Minimal bootstrapper that defers to Fulcrum's module toggles before Paper instantiates the plugin.
 */
public final class DuelsBootstrapper implements PluginBootstrap {
    private boolean shouldLoad;

    @Override
    public void bootstrap(BootstrapContext context) {
        try {
            BootstrapContextHolder.setContext(DuelsPlugin.MODULE_ID);

            if (!FulcrumEnvironment.isThisModuleEnabled()) {
                context.getLogger().info("Duels module disabled - skipping load");
                shouldLoad = false;
                return;
            }

            shouldLoad = true;
            context.getLogger().info("Duels module enabled");
        } finally {
            BootstrapContextHolder.clearContext();
        }
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        if (!shouldLoad) {
            throw new IllegalStateException("Aborting load: Duels module disabled via Fulcrum configuration.");
        }
        return PluginBootstrap.super.createPlugin(context);
    }
}
