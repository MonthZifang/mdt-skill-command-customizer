package com.mdt.skill;

import arc.util.CommandHandler;
import arc.util.Log;
import com.mdt.skill.config.SkillPluginConfiguration;
import com.mdt.skill.service.SkillCommandService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import mindustry.Vars;
import mindustry.mod.Plugin;

public final class SkillCommandCustomizerPlugin extends Plugin {
    private static final String CONFIG_DIR_NAME = "mdt-skill-command-customizer";
    private static final String CONFIG_FILE_NAME = "skill-command-customizer.properties";

    private File dataRoot;
    private SkillPluginConfiguration configuration;
    private SkillCommandService service;

    @Override
    public void init() {
        try {
            dataRoot = resolveDataRoot();
            ensureDefaultResources();
            reloadInternal();
            Log.info("MDT Skill Command Customizer loaded.");
            Log.info("Config file: @", new File(dataRoot, CONFIG_FILE_NAME).getAbsolutePath());
        } catch (IOException exception) {
            throw new RuntimeException("Failed to initialize MDT Skill Command Customizer.", exception);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("skill-customizer-reload", "Reload skill command configuration.", args -> {
            try {
                reloadInternal();
                Log.info("Skill commands reloaded: @", service.describeSkills());
            } catch (IOException exception) {
                Log.err("Failed to reload skill commands: @", exception.getMessage());
            }
        });

        handler.register("skill-customizer-list", "List configured skill commands.", args -> {
            Log.info("Skill commands: @", service.describeSkills());
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        service.registerClientCommands(handler);
    }

    private void reloadInternal() throws IOException {
        configuration = SkillPluginConfiguration.load(new File(dataRoot, CONFIG_FILE_NAME));
        service = new SkillCommandService(configuration);
    }

    private File resolveDataRoot() {
        File modsRoot = new File(Vars.dataDirectory.absolutePath(), "mods");
        return new File(new File(modsRoot, "config"), CONFIG_DIR_NAME);
    }

    private void ensureDefaultResources() throws IOException {
        if (!dataRoot.exists() && !dataRoot.mkdirs() && !dataRoot.isDirectory()) {
            throw new IOException("Unable to create config directory: " + dataRoot.getAbsolutePath());
        }
        copyIfMissing(CONFIG_FILE_NAME);
    }

    private void copyIfMissing(String resourceName) throws IOException {
        File target = new File(dataRoot, resourceName);
        if (target.exists()) {
            return;
        }
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Missing bundled resource: " + resourceName);
            }
            Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
