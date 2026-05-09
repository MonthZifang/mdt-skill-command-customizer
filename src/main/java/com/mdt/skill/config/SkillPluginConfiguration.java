package com.mdt.skill.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class SkillPluginConfiguration {
    private final Map<String, PermissionGroup> permissionGroups;
    private final List<SkillDefinition> skills;

    private SkillPluginConfiguration(Map<String, PermissionGroup> permissionGroups, List<SkillDefinition> skills) {
        this.permissionGroups = permissionGroups;
        this.skills = skills;
    }

    public static SkillPluginConfiguration load(File file) throws IOException {
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        Map<String, PermissionGroup> groups = new LinkedHashMap<String, PermissionGroup>();
        for (String groupId : splitCsv(properties.getProperty("group.keys"))) {
            String prefix = "group." + groupId + ".";
            String mode = valueOrDefault(properties.getProperty(prefix + "mode"), "all");
            Set<String> players = new LinkedHashSet<String>(splitCsv(properties.getProperty(prefix + "players")));
            groups.put(groupId, new PermissionGroup(groupId, mode, players));
        }
        if (!groups.containsKey("default")) {
            groups.put("default", new PermissionGroup("default", "all", new LinkedHashSet<String>()));
        }

        List<SkillDefinition> skills = new ArrayList<SkillDefinition>();
        for (String skillId : splitCsv(properties.getProperty("skill.keys"))) {
            String basePrefix = "skill." + skillId + ".";
            String command = valueOrDefault(properties.getProperty(basePrefix + "command"), skillId);
            String description = valueOrDefault(properties.getProperty(basePrefix + "description"), "Configured skill command");
            String argsHelp = valueOrDefault(properties.getProperty(basePrefix + "argsHelp"), "[args...]");
            List<String> allowedGroups = splitCsv(properties.getProperty(basePrefix + "allowedGroups"));
            if (allowedGroups.isEmpty()) {
                allowedGroups.add("default");
            }

            List<CostDefinition> costs = new ArrayList<CostDefinition>();
            for (String costId : splitCsv(properties.getProperty(basePrefix + "cost.keys"))) {
                String costPrefix = basePrefix + "cost." + costId + ".";
                costs.add(new CostDefinition(
                    costId,
                    valueOrDefault(properties.getProperty(costPrefix + "type"), "currency"),
                    valueOrDefault(properties.getProperty(costPrefix + "currency"), "gold"),
                    valueOrDefault(properties.getProperty(costPrefix + "amountSource"), "literal:0"),
                    valueOrDefault(properties.getProperty(costPrefix + "displayName"), costId)
                ));
            }

            List<EffectDefinition> effects = new ArrayList<EffectDefinition>();
            for (String effectId : splitCsv(properties.getProperty(basePrefix + "effect.keys"))) {
                String effectPrefix = basePrefix + "effect." + effectId + ".";
                effects.add(new EffectDefinition(
                    effectId,
                    valueOrDefault(properties.getProperty(effectPrefix + "type"), "random-block-under-player"),
                    valueOrDefault(properties.getProperty(effectPrefix + "block"), ""),
                    splitCsv(properties.getProperty(effectPrefix + "blocks")),
                    valueOrDefault(properties.getProperty(effectPrefix + "countSource"), "literal:1"),
                    parseInt(properties.getProperty(effectPrefix + "radius"), 0),
                    parseBoolean(properties.getProperty(effectPrefix + "replaceExisting"), true),
                    valueOrDefault(properties.getProperty(effectPrefix + "successMessage"), "")
                ));
            }

            skills.add(new SkillDefinition(skillId, command, description, argsHelp, allowedGroups, costs, effects));
        }

        return new SkillPluginConfiguration(groups, skills);
    }

    public Map<String, PermissionGroup> getPermissionGroups() {
        return permissionGroups;
    }

    public List<SkillDefinition> getSkills() {
        return skills;
    }

    private static List<String> splitCsv(String raw) {
        List<String> result = new ArrayList<String>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        for (String part : raw.split(",")) {
            String value = part == null ? "" : part.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    public static final class PermissionGroup {
        private final String id;
        private final String mode;
        private final Set<String> players;

        private PermissionGroup(String id, String mode, Set<String> players) {
            this.id = id;
            this.mode = mode;
            this.players = players;
        }

        public String getId() {
            return id;
        }

        public String getMode() {
            return mode;
        }

        public Set<String> getPlayers() {
            return players;
        }
    }

    public static final class SkillDefinition {
        private final String id;
        private final String command;
        private final String description;
        private final String argsHelp;
        private final List<String> allowedGroups;
        private final List<CostDefinition> costs;
        private final List<EffectDefinition> effects;

        private SkillDefinition(
            String id,
            String command,
            String description,
            String argsHelp,
            List<String> allowedGroups,
            List<CostDefinition> costs,
            List<EffectDefinition> effects
        ) {
            this.id = id;
            this.command = command;
            this.description = description;
            this.argsHelp = argsHelp;
            this.allowedGroups = allowedGroups;
            this.costs = costs;
            this.effects = effects;
        }

        public String getId() {
            return id;
        }

        public String getCommand() {
            return command;
        }

        public String getDescription() {
            return description;
        }

        public String getArgsHelp() {
            return argsHelp;
        }

        public List<String> getAllowedGroups() {
            return allowedGroups;
        }

        public List<CostDefinition> getCosts() {
            return costs;
        }

        public List<EffectDefinition> getEffects() {
            return effects;
        }
    }

    public static final class CostDefinition {
        private final String id;
        private final String type;
        private final String currency;
        private final String amountSource;
        private final String displayName;

        private CostDefinition(String id, String type, String currency, String amountSource, String displayName) {
            this.id = id;
            this.type = type;
            this.currency = currency;
            this.amountSource = amountSource;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getCurrency() {
            return currency;
        }

        public String getAmountSource() {
            return amountSource;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static final class EffectDefinition {
        private final String id;
        private final String type;
        private final String block;
        private final List<String> blocks;
        private final String countSource;
        private final int radius;
        private final boolean replaceExisting;
        private final String successMessage;

        private EffectDefinition(
            String id,
            String type,
            String block,
            List<String> blocks,
            String countSource,
            int radius,
            boolean replaceExisting,
            String successMessage
        ) {
            this.id = id;
            this.type = type;
            this.block = block;
            this.blocks = blocks;
            this.countSource = countSource;
            this.radius = Math.max(0, radius);
            this.replaceExisting = replaceExisting;
            this.successMessage = successMessage;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getBlock() {
            return block;
        }

        public List<String> getBlocks() {
            return blocks;
        }

        public String getCountSource() {
            return countSource;
        }

        public int getRadius() {
            return radius;
        }

        public boolean isReplaceExisting() {
            return replaceExisting;
        }

        public String getSuccessMessage() {
            return successMessage;
        }
    }
}
