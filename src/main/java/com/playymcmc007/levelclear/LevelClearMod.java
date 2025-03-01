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
            logger.info("正在关闭服务器并删除存档...");

            // 异步删除存档
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    deleteWorldWithRetry(Objects.requireNonNull(world.getMinecraftServer()), 10);
                } catch (InterruptedException e) {
                    logger.warning("关闭服务器并删除存档时发生错误: " + e.getMessage());
                }
            }).start();
        } else {
            logger.info("触发玩家数量不足，跳过存档删除。");
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
        if (currentTime - lastMessageTime >= 60000) {
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
            logger.info("存档已删除: " + worldDir.getAbsolutePath());
            return true;
        } catch (IOException e) {
            if (remainingAttempts > 0) {
                server.addScheduledTask(() -> {
                    logger.warning("存档删除失败，剩余重试次数: " + remainingAttempts + "，0.5秒后重试... ");
                    try {
                        Thread.sleep(500);
                        deleteWorldWithRetry(server, remainingAttempts - 1);
                    } catch (InterruptedException ignored) {}
                });
            } else {
                logger.warning("存档删除失败，路径: " + worldDir.getAbsolutePath());
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
            throw new IOException("无法删除文件: " + folder.getAbsolutePath());
        }
    }
}