package com.playymcmc007.levelclear;
import com.playymcmc007.levelclear.config.LevelClearConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

@Mod(
        modid = "levelclear",
        name = "LevelClear",
        version = "1.0.0",
        acceptableRemoteVersions = "*"
)
public class LevelClearMod {
    private long lastMessageTime = 0;
    private static final Logger logger = Logger.getLogger("LevelClearMod");

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        ConfigManager.sync("levelclear", Config.Type.INSTANCE); // 加载配置
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        World world = net.minecraftforge.common.DimensionManager.getWorld(0);
        if (world == null) return;

        LevelClearState state = LevelClearState.get(world);
        if (state == null) return;

        for (EntityPlayerMP player : Objects.requireNonNull(world.getMinecraftServer()).getPlayerList().getPlayers()) {
            boolean hasTriggerItem = checkTriggerItem(player);

            if (hasTriggerItem && !state.getTriggeredPlayers().contains(player.getName())) {
                state.setTriggered(true);
                state.addTriggeredPlayer(player.getName());
                state.markDirty();
            }
        }

        if (state.isTriggered()) {
            triggerLevelClear(world.getMinecraftServer(), state);
        }
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
            String advancementId = event.getAdvancement().getId().toString();

            if (LevelClearConfig.triggerAdvancements != null) {
                for (String triggerAdvancement : LevelClearConfig.triggerAdvancements) {
                    if (triggerAdvancement.equals(advancementId)) {
                        LevelClearState state = LevelClearState.get(player.world);
                        if (state != null && !state.getTriggeredPlayers().contains(player.getName())) {
                            state.setTriggered(true);
                            state.addTriggeredPlayer(player.getName());
                            state.markDirty();
                        }
                        break;
                    }
                }
            }
        }
    }

    @Mod.EventHandler
    public void onServerStop(FMLServerStoppingEvent event) {
        World world = net.minecraftforge.common.DimensionManager.getWorld(0);
        if (world == null) return;

        LevelClearState state = LevelClearState.get(world);
        if (state == null || !state.isTriggered()){ return;
}
        if (LevelClearConfig.destroySaveOnExit &&
                state.getTriggeredPlayers().size() >= LevelClearConfig.minPlayersToDestroySave) {
            if (LevelClearConfig.showMultiLanguageLogs) {
                // 同时输出中文和英文日志
                logger.info("[EN] Shutting down the server and deleting the world...");
                logger.info("[ZH] 正在关闭服务器并删除存档...");
            } else {
                // 只输出英文日志
                logger.info("Shutting down the server and deleting the world...");
            }

            // 异步删除存档
            new Thread(() -> {
                try {
                    // 使用配置项的值
                    Thread.sleep(LevelClearConfig.deleteWorldDelayMillis);
                    deleteWorldWithRetry(Objects.requireNonNull(world.getMinecraftServer()), LevelClearConfig.deleteWorldRetryAttempts);
                } catch (InterruptedException e) {
                    if (LevelClearConfig.showMultiLanguageLogs) {
                        // 同时输出中文和英文日志
                        logger.warning("[EN] An error occurred while shutting down the server and deleting the world: " + e.getMessage());
                        logger.warning("[ZH] 关闭服务器并删除存档时发生错误: " + e.getMessage());
                    } else {
                        // 只输出英文日志
                        logger.warning("An error occurred while shutting down the server and deleting the world: " + e.getMessage());
                    }
                }
            }).start();
        } else {
            if (LevelClearConfig.showMultiLanguageLogs) {
                // 同时输出中文和英文日志
                logger.info("[EN] Not enough triggering players. Skipping world deletion.");
                logger.info("[ZH] 触发玩家数量不足，跳过存档删除。");
            } else {
                // 只输出英文日志
                logger.info("Not enough triggering players. Skipping world deletion.");
            }
        }
    }

    private boolean checkTriggerItem(EntityPlayerMP player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isTriggerItem(stack)) return true;
        }

        ItemStack offhand = player.inventory.offHandInventory.get(0);
        return isTriggerItem(offhand);
    }

    private boolean isTriggerItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = Objects.requireNonNull(Item.REGISTRY.getNameForObject(stack.getItem())).toString();
        if (LevelClearConfig.triggerItems != null) {
            for (String triggerItem : LevelClearConfig.triggerItems) {
                if (triggerItem.equals(itemId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void triggerLevelClear(net.minecraft.server.MinecraftServer server, LevelClearState state) {
        state.setTriggered(true);
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMessageTime >= LevelClearConfig.messageCooldown) {
            String playerNames = String.join("、", state.getTriggeredPlayers());
            String message = LevelClearConfig.chatMessage.replace("<player>", playerNames);
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                player.sendMessage(new TextComponentString(message));
            }
            lastMessageTime = currentTime;
        }
        for (String playerName : state.getTriggeredPlayers()) {
            EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(playerName);
            if (target != null) {
                target.setGameType(net.minecraft.world.GameType.SPECTATOR);
            }
        }


    }

    private boolean deleteWorldWithRetry(net.minecraft.server.MinecraftServer server, int remainingAttempts) {
        File worldDir = server.getFile("saves/" + server.getFolderName());

        try {
            deleteFolder(worldDir);
            if (LevelClearConfig.showMultiLanguageLogs) {
                logger.info("[EN] World deleted: " + worldDir.getAbsolutePath());
                logger.info("[ZH] 存档已删除: " + worldDir.getAbsolutePath());
            } else {
                logger.info("存档已删除: " + worldDir.getAbsolutePath());
            }
            return true;
        } catch (IOException e) {
            if (remainingAttempts > 0) {
                server.addScheduledTask(() -> {
                    // 计算时间描述（秒）
                    double retryDelaySeconds = LevelClearConfig.retryDelayMillis / 1000.0;
                    String retryDelayDescription = String.format("%.3f", retryDelaySeconds).replaceAll("\\.?0+$", ""); // 保留小数点后三位，并去掉末尾多余的0

                    // 输出日志
                    if (LevelClearConfig.showMultiLanguageLogs) {
                        logger.warning("[EN] Failed to delete world. Remaining attempts: " + remainingAttempts + ", retrying in " + retryDelayDescription + " seconds...");
                        logger.warning("[ZH] 存档删除失败，剩余重试次数: " + remainingAttempts + "，" + retryDelayDescription + "秒后重试...");
                    } else {
                        logger.warning("存档删除失败，剩余重试次数: " + remainingAttempts + "，" + retryDelayDescription + "秒后重试...");
                    }

                    try {
                        Thread.sleep(LevelClearConfig.retryDelayMillis); // 使用配置项的值
                        deleteWorldWithRetry(server, remainingAttempts - 1);
                    } catch (InterruptedException ignored) {
                    }
                });
            } else {
                if (LevelClearConfig.showMultiLanguageLogs) {
                    logger.warning("[EN] Failed to delete world: " + worldDir.getAbsolutePath());
                    logger.warning("[ZH] 存档删除失败，路径: " + worldDir.getAbsolutePath());
                } else {
                    logger.warning("存档删除失败，路径: " + worldDir.getAbsolutePath());
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
            if (LevelClearConfig.showMultiLanguageLogs) {
                throw new IOException("无法删除文件: " + folder.getAbsolutePath() + " (Failed to delete file: " + folder.getAbsolutePath() + ")");
            } else {
                throw new IOException("无法删除文件: " + folder.getAbsolutePath());
            }
        }
    }
}