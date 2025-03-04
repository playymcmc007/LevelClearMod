package com.playymcmc007.levelclear;

import com.playymcmc007.levelclear.config.LevelClearConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import net.minecraft.registry.Registries;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class LevelClearMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LevelClearMod.class);
    private static LevelClearConfig config;
    private long lastMessageTime = 0;
    @Override
    public void onInitialize() {
        AutoConfig.register(LevelClearConfig.class, Toml4jConfigSerializer::new);
        config = AutoConfig.getConfigHolder(LevelClearConfig.class).getConfig();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            LevelClearState state = LevelClearState.getServerState(server);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                boolean hasTriggerItem = false;

                for (ItemStack stack : player.getInventory().main) {
                    if (isTriggerItem(stack)) {
                        hasTriggerItem = true;
                        break;
                    }
                }

                if (!hasTriggerItem) {
                    ItemStack offhandStack = player.getInventory().offHand.getFirst();
                    if (isTriggerItem(offhandStack)) {
                        hasTriggerItem = true;
                    }
                }

                if (hasTriggerItem && !state.getTriggeredPlayers().contains(player.getName().getString())) {
                    state.setTriggered(true);
                    state.addTriggeredPlayer(player.getName().getString());
                }
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PlayerAdvancementTracker tracker = player.getAdvancementTracker();
                for (String advancementId : config.triggerAdvancements) {
                    String[] parts = advancementId.split(":");
                    if (parts.length != 2) {
                        continue;
                    }
                    Identifier identifier = Identifier.of(parts[0], parts[1]);
                    AdvancementEntry advancement = server.getAdvancementLoader().get(identifier);
                    if (advancement != null && tracker.getProgress(advancement).isDone()) {
                        if (!state.getTriggeredPlayers().contains(player.getName().getString())) {
                            state.setTriggered(true);
                            state.addTriggeredPlayer(player.getName().getString());
                        }
                        break;
                    }
                }
            }
            if (state.isTriggered()) {
                triggerLevelClear(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LevelClearState state = LevelClearState.getServerState(server);

            if (state.isTriggered() && config.destroySaveOnExit) {
                boolean meetMinPlayers = state.getTriggeredPlayers().size() >= config.minPlayersToDestroySave;

                if (meetMinPlayers) {
                    if (config.showMultiLanguageLogs) {
                        LOGGER.info("[EN] All triggering players have gone offline. Shutting down the server and deleting the world...");
                        LOGGER.info("[ZH] 所有触发玩家已离线，正在关闭服务器并删除存档...");
                    } else {
                        LOGGER.info("All triggering players have gone offline. Shutting down the server and deleting the world...");
                    }
                    try {
                        boolean success = deleteWorldWithRetry(server, config.deleteWorldRetryAttempts);
                        if (!success) {
                            if (config.showMultiLanguageLogs) {
                                LOGGER.error("[EN] Failed to delete the world. Please check the world folder manually.");
                                LOGGER.error("[ZH] 存档删除失败，请手动检查存档文件夹。");
                            } else {
                                LOGGER.error("Failed to delete the world. Please check the world folder manually.");
                            }
                        }
                    } catch (Exception e) {
                        if (config.showMultiLanguageLogs) {
                            LOGGER.error("[EN] An error occurred while shutting down the server: {}", e.getMessage());
                            LOGGER.error("[ZH] 关闭服务器时发生错误: {}", e.getMessage());
                        } else {
                            LOGGER.error("An error occurred while shutting down the server: {}", e.getMessage());
                        }
                    }
                } else {
                    if (config.showMultiLanguageLogs) {
                        LOGGER.info("[EN] Not enough triggering players. Skipping world deletion.");
                        LOGGER.info("[ZH] 触发玩家数量不足，跳过存档删除。");
                    } else {
                        LOGGER.info("Not enough triggering players. Skipping world deletion.");
                    }
                }
            }
        });
    }

    private boolean isTriggerItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Identifier itemId = Registries.ITEM.getId(stack.getItem());

        return config.triggerItems.contains(itemId.toString());
    }

    private void triggerLevelClear(MinecraftServer server) {
        LevelClearState state = LevelClearState.getServerState(server);
        state.setTriggered(true);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastMessageTime >= config.messageCooldown) {
            String allPlayerNames = state.getTriggeredPlayers().isEmpty() ? "未知玩家" : String.join("、", state.getTriggeredPlayers());
            String message;
            if (config.chatMessage.isEmpty()) {
                message = Text.translatable("levelclear.message", allPlayerNames).getString();
            } else {
                message = config.chatMessage.replace("<player>", allPlayerNames);
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(Text.of(message), false);
            }
            lastMessageTime = currentTime;
        }

        // 模式修改逻辑
        for (String playerName : state.getTriggeredPlayers()) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getName().getString().equals(playerName)) {
                    player.changeGameMode(GameMode.SPECTATOR);
                    break;
                }
            }
        }
    }
    private boolean deleteWorldWithRetry(MinecraftServer server, int remainingAttempts) {
        File worldSaveDir = server.getSavePath(WorldSavePath.ROOT).toFile();
        String worldFolderName = worldSaveDir.getAbsolutePath();
        File worldFolder = new File(worldFolderName);

        if (!worldFolder.exists()) {
            if (config.showMultiLanguageLogs) {
                // 同时输出中文和英文日志
                LOGGER.error("[EN] World folder does not exist: {}", worldFolder.getAbsolutePath());
                LOGGER.error("[ZH] 存档文件夹不存在: {}", worldFolder.getAbsolutePath());
            } else {
                // 只输出英文日志
                LOGGER.error("World folder does not exist: {}", worldFolder.getAbsolutePath());
            }
            return false;
        }

        try {
            deleteFolder(worldFolder);
            if (config.showMultiLanguageLogs) {
                // 同时输出中文和英文日志
                LOGGER.info("[EN] World deleted: {}", worldFolder.getAbsolutePath());
                LOGGER.info("[ZH] 存档已删除: {}", worldFolder.getAbsolutePath());
            } else {
                // 只输出英文日志
                LOGGER.info("World deleted: {}", worldFolder.getAbsolutePath());
            }
            return true;
        } catch (IOException e) {
            if (remainingAttempts > 0) {
                double retryDelaySeconds = config.retryDelayMillis / 1000.0;
                String retryDelayDescription = String.format("%.3f", retryDelaySeconds).replaceAll("\\.?0+$", "");
                if (config.showMultiLanguageLogs) {
                    LOGGER.error("[EN] Failed to delete world. Remaining attempts: {}, retrying in {} seconds...", remainingAttempts, retryDelayDescription);
                    LOGGER.error("[ZH] 删除存档失败，剩余重试次数: {}，{}秒后重试...", remainingAttempts, retryDelayDescription);
                } else {
                    LOGGER.error("Failed to delete world. Remaining attempts: {}, retrying in {} seconds...", remainingAttempts, retryDelayDescription);
                }
                server.submit(() -> {
                    try {
                        Thread.sleep(config.retryDelayMillis);
                    } catch (InterruptedException ignored) {

                    }
                    deleteWorldWithRetry(server, remainingAttempts - 1);
                    return null;
                });
            } else {
                if (config.showMultiLanguageLogs) {
                    LOGGER.error("[EN] Failed to delete world: {}", e.getMessage());
                    LOGGER.error("[ZH] 存档删除失败: {}", e.getMessage());
                } else {
                    LOGGER.error("Failed to delete world: {}", e.getMessage());
                }
            }
            return false;
        }
    }

    private void deleteFolder(File folder) throws IOException {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file);
                }
            }
        }
        if (!folder.delete()) {
            throw new IOException("Failed to delete: " + folder.getAbsolutePath());
        }
    }
}
