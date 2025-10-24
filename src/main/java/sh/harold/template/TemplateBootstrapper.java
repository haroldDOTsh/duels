package sh.harold.template;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.module.BootstrapContextHolder;
import sh.harold.fulcrum.api.module.FulcrumEnvironment;

public final class TemplateBootstrapper implements PluginBootstrap {
    private boolean shouldLoad;

    @Override
    public void bootstrap(BootstrapContext context) {
        try {
            BootstrapContextHolder.setContext(Template.MODULE_ID); // No manual string needed—keeps module ID in one place

            if (!FulcrumEnvironment.isThisModuleEnabled()) {
                context.getLogger().info("Template disabled - will not load");
                shouldLoad = false;
                return;
            }

            shouldLoad = true;
            context.getLogger().info("Template enabled - will load");
        } finally {
            BootstrapContextHolder.clearContext();
        }
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        if (!shouldLoad) {
            throw new IllegalStateException("Aborting load: Template module disabled via Fulcrum configuration.");
        }
        return PluginBootstrap.super.createPlugin(context);
    }
}
