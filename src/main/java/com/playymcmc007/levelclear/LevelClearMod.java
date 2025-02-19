package com.playymcmc007.levelclear;

import com.playymcmc007.levelclear.config.LevelClearConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import org.apache.logging.log4j.LogManager;
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

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            LevelClearState state = LevelClearState.getServerState(server);
            if (!state.isTriggered()) {
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
                    if (hasTriggerItem) {
                        state.setTriggered(true);
                        state.addTriggeredPlayer(player.getName().getString());
                        break;
                    }
                }
            }
            if (state.isTriggered()) {
                triggerLevelClear(server);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            LevelClearState state = LevelClearState.getServerState(server);
            if (!state.isTriggered()) {
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
                            state.setTriggered(true);
                            state.addTriggeredPlayer(player.getName().getString());
                            break;
                        }
                    }
                }
            }
            if (state.isTriggered()) {
                triggerLevelClear(server);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LevelClearState state = LevelClearState.getServerState(server);
            if (state.isTriggered() && config.destroySaveOnExit) {
                deleteWorldWithRetry(server, 3);
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

        if (currentTime - lastMessageTime >= 60000) {
            String firstPlayerName = state.getTriggeredPlayers().isEmpty() ? "未知玩家" : state.getTriggeredPlayers().getFirst();

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                String message = config.chatMessage.replace("<player>", firstPlayerName);
                player.sendMessage(Text.of(message), false);
            }

            lastMessageTime = currentTime;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.changeGameMode(GameMode.SPECTATOR);
        }
    }

    private void deleteWorldWithRetry(MinecraftServer server, int remainingAttempts) {
        File worldSaveDir = server.getSavePath(WorldSavePath.ROOT).toFile();
        String worldFolderName = worldSaveDir.getAbsolutePath();
        File worldFolder = new File(worldFolderName);

        if (!worldFolder.exists()) {
            LOGGER.error("存档文件夹不存在: {}", worldFolder.getAbsolutePath());
            return;
        }

        try {
            deleteFolder(worldFolder);
            LOGGER.info("存档已删除: " + worldFolder.getAbsolutePath());
        } catch (IOException e) {
            if (remainingAttempts > 0) {
                LOGGER.error("删除存档失败，剩余重试次数: {}，0.5秒后重试...", remainingAttempts);
                server.submit(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {

                    }
                    deleteWorldWithRetry(server, remainingAttempts - 1);
                    return null;
                });
            } else {
                LOGGER.error("存档删除失败: {}", e.getMessage());
            }
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
            throw new IOException("无法删除: " + folder.getAbsolutePath());
        }
    }
}