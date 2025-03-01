package com.playymcmc007.levelclear.config;

import net.minecraftforge.common.config.Config;

@Config(modid = "levelclear", category = "levelclear")
public class LevelClearConfig {
    // 触发物品列表
    @Config.Comment({"${config.levelclear.triggerItems}", "${config.levelclear.triggerItems.@Comment}"})
    public static String[] triggerItems = new String[]{"minecraft:elytra", "minecraft:nether_star"};

    // 触发进度列表
    @Config.Comment({"${config.levelclear.triggerAdvancements}", "${config.levelclear.triggerAdvancements.@Comment}"})
    public static String[] triggerAdvancements = new String[]{"minecraft:nether/create_beacon", "minecraft:end/elytra"};

    // 聊天消息
    @Config.Comment({"${config.levelclear.chatMessage}", "${config.levelclear.chatMessage.@Comment}"})
    public static String chatMessage = "<player> 通关了这个存档";

    // 退出时删除存档
    @Config.Comment({"${config.levelclear.destroySaveOnExit}", "${config.levelclear.destroySaveOnExit.@Comment"})
    public static boolean destroySaveOnExit = false;

    // 最小触发玩家数
    @Config.Comment({"${config.levelclear.minPlayersToDestroySave}", "${config.levelclear.minPlayersToDestroySave.@Tooltip}"})
    public static int minPlayersToDestroySave = 1;
}