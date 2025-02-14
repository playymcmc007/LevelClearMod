package com.playymcmc007.levelclear;

import com.playymcmc007.levelclear.Config.LevelClearConfig;
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

import java.io.File;
import java.io.IOException;

public class LevelClearMod implements ModInitializer {
	private static LevelClearConfig config;
	private long lastMessageTime = 0;
	@Override
	public void onInitialize() {
		// 加载配置文件
		AutoConfig.register(LevelClearConfig.class, Toml4jConfigSerializer::new);
		config = AutoConfig.getConfigHolder(LevelClearConfig.class).getConfig();

		// 监听服务器每 tick 事件
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			LevelClearState state = LevelClearState.getServerState(server);
			if (!state.isTriggered()) {

			// 遍历所有在线玩家
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					// 检查玩家物品栏是否包含触发物品
					boolean hasTriggerItem = false;

					// 遍历玩家物品栏（包括背包和快捷栏）
					for (ItemStack stack : player.getInventory().main) {
						if (isTriggerItem(stack)) {
							hasTriggerItem = true;
							break;
						}
					}
					// 如果未找到，继续检查副手
					if (!hasTriggerItem) {
						ItemStack offhandStack = player.getInventory().offHand.get(0);
						if (isTriggerItem(offhandStack)) {
							hasTriggerItem = true;
						}
					}
					// 如果发现触发物品，执行逻辑
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
		// 监听玩家获得成就事件
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
				boolean success = deleteWorld(server); // 直接调用，无需传递 worldName
				if (!success) {
					System.err.println("存档删除失败，请手动检查存档文件夹。");
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
			// 获取第一位触发玩家的名字
			String firstPlayerName = state.getTriggeredPlayers().isEmpty() ? "未知玩家" : state.getTriggeredPlayers().get(0);

			// 显示聊天消息，替换 <player> 为第一位触发玩家
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				String message = config.chatMessage.replace("<player>", firstPlayerName);
				player.sendMessage(Text.of(message), false);
			}

			// 更新上次显示消息的时间
			lastMessageTime = currentTime;
		}

		// 模式修改逻辑
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			player.changeGameMode(GameMode.SPECTATOR);
		}
	}
	private void deleteWorldWithRetry(MinecraftServer server, int remainingAttempts) {
		// 获取存档文件夹路径
		File worldSaveDir = server.getSavePath(WorldSavePath.ROOT).toFile();
		String worldFolderName = worldSaveDir.getAbsolutePath(); // 获取存档文件夹目录
		//输出绝对路径

		// 构建存档文件夹路径
		File worldFolder = new File(worldFolderName);

		// 检查存档文件夹是否存在（如果触发说明有bug）
		if (!worldFolder.exists()) {
			System.err.println("存档文件夹不存在: " + worldFolder.getAbsolutePath());
			return;
		}

		try {
			deleteFolder(worldFolder);
			System.out.println("存档已删除: " + worldFolder.getAbsolutePath());
		} catch (IOException e) {
			if (remainingAttempts > 0) {
				System.err.println("删除存档失败，剩余重试次数: " + remainingAttempts + "，0.5秒后重试...");
				//当游戏退出但玩家数据未完全退出时可能导致删除失败，属正常现象，因此加上了重试逻辑
				// 使用服务器线程调度器延迟执行重试
				server.submit(() -> {
					try {
						Thread.sleep(500); // 延迟500ms（0.5秒）
					} catch (InterruptedException ignored) {
						// 忽略中断异常
					}
					deleteWorldWithRetry(server, remainingAttempts - 1);
					return null; // 返回 null 以匹配 CompletableFuture<Void> 的签名
				});
			} else {
				//一般情况下不会触发
				System.err.println("存档删除失败: " + e.getMessage());
			}
		}
	}

	// 重试逻辑
	private boolean deleteWorld(MinecraftServer server) {
		deleteWorldWithRetry(server, 3); // 最多重试3次，可修改
		return true;
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