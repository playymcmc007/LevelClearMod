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

            // 检查玩家是否持有触发物品
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                boolean hasTriggerItem = false;

                // 检查主手物品栏
                for (ItemStack stack : player.getInventory().main) {
                    if (isTriggerItem(stack)) {
                        hasTriggerItem = true;
                        break;
                    }
                }

                // 检查副手物品栏
                if (!hasTriggerItem) {
                    ItemStack offhandStack = player.getInventory().offHand.getFirst();
                    if (isTriggerItem(offhandStack)) {
                        hasTriggerItem = true;
                    }
                }

                // 如果玩家持有触发物品，添加到触发列表
                if (hasTriggerItem && !state.getTriggeredPlayers().contains(player.getName().getString())) {
                    state.setTriggered(true);
                    state.addTriggeredPlayer(player.getName().getString());
                }
            }

            // 检查玩家是否完成指定进度
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
                // 检查是否满足最小玩家数量
                boolean meetMinPlayers = state.getTriggeredPlayers().size() >= config.minPlayersToDestroySave;

                if (meetMinPlayers) {
                    LOGGER.info("所有触发玩家已离线，正在关闭服务器并删除存档...");

                    try {
                        boolean success = deleteWorldWithRetry(server, 3);
                        if (!success) {
                            LOGGER.error("存档删除失败，请手动检查存档文件夹。");
                        }
                    } catch (Exception e) {
                        LOGGER.error("关闭服务器时发生错误: {}", e.getMessage());
                    }
                } else {
                    LOGGER.info("触发玩家数量不足，跳过存档删除。");
                }
            }
        });
    }


    // 辅助方法：判断物品是否为触发物品
    private boolean isTriggerItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false; // 如果物品堆栈为空，返回 false
        }

        // 获取物品的注册名（Identifier）
        Identifier itemId = Registries.ITEM.getId(stack.getItem());

        // 检查物品的注册名是否在配置文件的 triggerItems 列表中
        return config.triggerItems.contains(itemId.toString());
    }

    private void triggerLevelClear(MinecraftServer server) {
        LevelClearState state = LevelClearState.getServerState(server);
        state.setTriggered(true); // 标记为已触发
        long currentTime = System.currentTimeMillis(); // 获取当前时间

        // 检查是否超过 1 分钟
        if (currentTime - lastMessageTime >= 60000) {
            // 获取触发玩家的名字
            String allPlayerNames = state.getTriggeredPlayers().isEmpty() ? "未知玩家" : String.join("、", state.getTriggeredPlayers());
            // 显示聊天消息，替换 <player> 为所有触发过的玩家名字
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                String message = config.chatMessage.replace("<player>", allPlayerNames);
                player.sendMessage(Text.of(message), false);
            }

            // 更新上次显示消息的时间
            lastMessageTime = currentTime;
        }

        // 模式修改逻辑
        for (String playerName : state.getTriggeredPlayers()) {
            // 获取当前在线玩家列表
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // 检查玩家是否在触发列表中且在线
                if (player.getName().getString().equals(playerName)) {
                    player.changeGameMode(GameMode.SPECTATOR);
                    break; // 找到玩家后跳出内层循环
                }
            }
        }
    }
    private boolean deleteWorldWithRetry(MinecraftServer server, int remainingAttempts) {
        File worldSaveDir = server.getSavePath(WorldSavePath.ROOT).toFile();
        String worldFolderName = worldSaveDir.getAbsolutePath();
        File worldFolder = new File(worldFolderName);

        if (!worldFolder.exists()) {
            LOGGER.error("存档文件夹不存在: {}", worldFolder.getAbsolutePath());
            return false;
        }

        try {
            deleteFolder(worldFolder);
            LOGGER.info("存档已删除: {}", worldFolder.getAbsolutePath());
            return true;
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
            throw new IOException("无法删除: " + folder.getAbsolutePath());
        }
    }
}
