package com.mdt.skill.service;

import arc.util.CommandHandler;
import com.mdt.economy.PlayerEconomySystemPlugin;
import com.mdt.economy.api.PlayerEconomyApi;
import com.mdt.economy.util.PlayerUuidResolver;
import com.mdt.skill.config.SkillPluginConfiguration;
import com.mdt.skill.config.SkillPluginConfiguration.CostDefinition;
import com.mdt.skill.config.SkillPluginConfiguration.EffectDefinition;
import com.mdt.skill.config.SkillPluginConfiguration.PermissionGroup;
import com.mdt.skill.config.SkillPluginConfiguration.SkillDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Player;
import mindustry.world.Block;
import mindustry.world.Tile;

public final class SkillCommandService {
    private final SkillPluginConfiguration configuration;
    private final Random random = new Random();

    public SkillCommandService(SkillPluginConfiguration configuration) {
        this.configuration = configuration;
    }

    public void registerClientCommands(CommandHandler handler) {
        for (SkillDefinition skill : configuration.getSkills()) {
            handler.<Player>register(skill.getCommand(), skill.getArgsHelp(), skill.getDescription(), (args, player) -> {
                executeSkill(skill, args, player);
            });
        }
    }

    public String describeSkills() {
        StringBuilder builder = new StringBuilder();
        for (SkillDefinition skill : configuration.getSkills()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("/").append(skill.getCommand());
        }
        return builder.length() == 0 ? "<none>" : builder.toString();
    }

    private void executeSkill(SkillDefinition skill, String[] args, Player player) {
        if (!hasPermission(skill, player)) {
            player.sendMessage("[scarlet]你没有权限使用这个技能。");
            return;
        }

        PlayerEconomyApi economyApi = PlayerEconomySystemPlugin.getApi();
        if (!skill.getCosts().isEmpty() && economyApi == null) {
            player.sendMessage("[scarlet]经济系统未加载，无法扣费。");
            return;
        }

        String uuid = PlayerUuidResolver.resolvePlayerUuid(player);
        List<ResolvedCost> resolvedCosts = resolveCosts(skill, args, uuid, economyApi);
        if (resolvedCosts == null) {
            player.sendMessage("[scarlet]技能配置中的消耗数量无效。");
            return;
        }

        for (ResolvedCost cost : resolvedCosts) {
            if (!economyApi.canAfford(uuid, cost.currency, cost.amount)) {
                player.sendMessage("[scarlet]余额不足:[] " + cost.displayName + " 需要 " + cost.amount);
                return;
            }
        }

        List<PreparedPlacement> placements = preparePlacements(skill, args, player);
        if (placements == null) {
            return;
        }

        for (ResolvedCost cost : resolvedCosts) {
            if (cost.amount > 0L) {
                economyApi.deductBalance(uuid, cost.currency, cost.amount);
            }
        }

        for (PreparedPlacement placement : placements) {
            placement.tile.setNet(placement.block, player.team(), 0);
        }

        if (placements.isEmpty()) {
            player.sendMessage("[scarlet]没有可执行的技能效果。");
            return;
        }

        String customMessage = findSuccessMessage(skill);
        if (!customMessage.isEmpty()) {
            player.sendMessage(customMessage);
            return;
        }
        player.sendMessage("[accent]技能已执行[]，放置方块数量: " + placements.size());
    }

    private List<ResolvedCost> resolveCosts(SkillDefinition skill, String[] args, String uuid, PlayerEconomyApi economyApi) {
        List<ResolvedCost> resolved = new ArrayList<ResolvedCost>();
        for (CostDefinition cost : skill.getCosts()) {
            if (!"currency".equalsIgnoreCase(cost.getType())) {
                continue;
            }
            Long amount = resolveAmount(cost.getAmountSource(), args, uuid, economyApi);
            if (amount == null || amount.longValue() < 0L) {
                return null;
            }
            resolved.add(new ResolvedCost(cost.getCurrency(), cost.getDisplayName(), amount.longValue()));
        }
        return resolved;
    }

    private List<PreparedPlacement> preparePlacements(SkillDefinition skill, String[] args, Player player) {
        List<PreparedPlacement> placements = new ArrayList<PreparedPlacement>();
        for (EffectDefinition effect : skill.getEffects()) {
            if ("random-block-under-player".equalsIgnoreCase(effect.getType())) {
                List<Block> blocks = resolveBlocks(effect);
                if (blocks.isEmpty()) {
                    player.sendMessage("[scarlet]技能没有可用方块配置。");
                    return null;
                }
                Long count = resolveAmount(effect.getCountSource(), args, PlayerUuidResolver.resolvePlayerUuid(player), PlayerEconomySystemPlugin.getApi());
                if (count == null || count.longValue() <= 0L) {
                    player.sendMessage("[scarlet]技能数量配置无效。");
                    return null;
                }
                List<Tile> candidates = collectCandidateTiles(player, effect.getRadius(), effect.isReplaceExisting());
                if (candidates.isEmpty()) {
                    player.sendMessage("[scarlet]脚下附近没有可放置的格子。");
                    return null;
                }
                Collections.shuffle(candidates, random);
                int limit = (int) Math.min((long) candidates.size(), count.longValue());
                for (int index = 0; index < limit; index++) {
                    Block block = blocks.get(random.nextInt(blocks.size()));
                    placements.add(new PreparedPlacement(candidates.get(index), block));
                }
                continue;
            }

            if ("block-under-player".equalsIgnoreCase(effect.getType())) {
                Block block = resolveBlock(effect.getBlock());
                if (block == null) {
                    player.sendMessage("[scarlet]技能配置的方块不存在:[] " + effect.getBlock());
                    return null;
                }
                Long count = resolveAmount(effect.getCountSource(), args, PlayerUuidResolver.resolvePlayerUuid(player), PlayerEconomySystemPlugin.getApi());
                if (count == null || count.longValue() <= 0L) {
                    player.sendMessage("[scarlet]技能数量配置无效。");
                    return null;
                }
                List<Tile> candidates = collectCandidateTiles(player, effect.getRadius(), effect.isReplaceExisting());
                if (candidates.isEmpty()) {
                    player.sendMessage("[scarlet]脚下附近没有可放置的格子。");
                    return null;
                }
                Collections.shuffle(candidates, random);
                int limit = (int) Math.min((long) candidates.size(), count.longValue());
                for (int index = 0; index < limit; index++) {
                    placements.add(new PreparedPlacement(candidates.get(index), block));
                }
            }
        }
        return placements;
    }

    private boolean hasPermission(SkillDefinition skill, Player player) {
        for (String groupId : skill.getAllowedGroups()) {
            PermissionGroup group = configuration.getPermissionGroups().get(groupId);
            if (group != null && matchesGroup(group, player)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGroup(PermissionGroup group, Player player) {
        if ("all".equalsIgnoreCase(group.getMode())) {
            return true;
        }
        if ("admin".equalsIgnoreCase(group.getMode())) {
            return player.admin;
        }
        if ("player".equalsIgnoreCase(group.getMode())) {
            String uuid = PlayerUuidResolver.resolvePlayerUuid(player);
            for (String value : group.getPlayers()) {
                if (value.equalsIgnoreCase("name:" + player.name) || value.equalsIgnoreCase("uuid:" + uuid)) {
                    return true;
                }
                if (value.equalsIgnoreCase(player.name) || value.equalsIgnoreCase(uuid)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Long resolveAmount(String source, String[] args, String uuid, PlayerEconomyApi economyApi) {
        if (source == null || source.trim().isEmpty()) {
            return Long.valueOf(0L);
        }
        String normalized = source.trim();
        if (normalized.startsWith("literal:")) {
            return parseLong(normalized.substring("literal:".length()));
        }
        if (normalized.startsWith("arg:")) {
            Integer index = parseInt(normalized.substring("arg:".length()));
            if (index == null || index.intValue() < 0 || index.intValue() >= args.length) {
                return null;
            }
            return parseLong(args[index.intValue()]);
        }
        if (normalized.startsWith("balance:")) {
            if (economyApi == null) {
                return null;
            }
            return Long.valueOf(economyApi.getBalance(uuid, normalized.substring("balance:".length()).trim()));
        }
        if (normalized.startsWith("random:")) {
            String range = normalized.substring("random:".length()).trim();
            String[] parts = range.split("-");
            if (parts.length != 2) {
                return null;
            }
            Long min = parseLong(parts[0]);
            Long max = parseLong(parts[1]);
            if (min == null || max == null || min.longValue() > max.longValue()) {
                return null;
            }
            long delta = max.longValue() - min.longValue();
            return Long.valueOf(min.longValue() + (delta <= 0L ? 0L : random.nextInt((int) delta + 1)));
        }
        return parseLong(normalized);
    }

    private List<Block> resolveBlocks(EffectDefinition effect) {
        List<Block> result = new ArrayList<Block>();
        for (String blockName : effect.getBlocks()) {
            Block block = resolveBlock(blockName);
            if (block != null) {
                result.add(block);
            }
        }
        if (result.isEmpty() && !effect.getBlock().isEmpty()) {
            Block single = resolveBlock(effect.getBlock());
            if (single != null) {
                result.add(single);
            }
        }
        return result;
    }

    private Block resolveBlock(String blockName) {
        if (blockName == null || blockName.trim().isEmpty()) {
            return null;
        }
        String normalized = blockName.trim();
        if ("air".equalsIgnoreCase(normalized)) {
            return Blocks.air;
        }
        return Vars.content.blocks().find(block -> normalized.equalsIgnoreCase(block.name));
    }

    private List<Tile> collectCandidateTiles(Player player, int radius, boolean replaceExisting) {
        List<Tile> result = new ArrayList<Tile>();
        Tile center = player.tileOn();
        if (center == null) {
            center = Vars.world.tileWorld(player.x, player.y);
        }
        if (center == null) {
            return result;
        }
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetY = -radius; offsetY <= radius; offsetY++) {
                Tile tile = Vars.world.tile(center.x + offsetX, center.y + offsetY);
                if (tile == null) {
                    continue;
                }
                if (!replaceExisting && tile.block() != Blocks.air) {
                    continue;
                }
                result.add(tile);
            }
        }
        return result;
    }

    private String findSuccessMessage(SkillDefinition skill) {
        for (EffectDefinition effect : skill.getEffects()) {
            if (effect.getSuccessMessage() != null && !effect.getSuccessMessage().trim().isEmpty()) {
                return effect.getSuccessMessage().trim();
            }
        }
        return "";
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(raw.trim()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseInt(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static final class ResolvedCost {
        private final String currency;
        private final String displayName;
        private final long amount;

        private ResolvedCost(String currency, String displayName, long amount) {
            this.currency = currency;
            this.displayName = displayName;
            this.amount = amount;
        }
    }

    private static final class PreparedPlacement {
        private final Tile tile;
        private final Block block;

        private PreparedPlacement(Tile tile, Block block) {
            this.tile = tile;
            this.block = block;
        }
    }
}
