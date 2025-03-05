package com.playymcmc007.levelclear.config;

import net.minecraftforge.common.config.Config;

@Config(modid = "levelclear", category = "levelclear")
public class LevelClearConfig {
    // 触发物品列表
    @Config.Comment({
            "List of item IDs that trigger the level clear.",
            "通关所指定的物品ID，可同时填写多个"
    })
    public static String[] triggerItems = new String[]{"minecraft:elytra", "minecraft:nether_star"};

    // 触发进度列表
    @Config.Comment({
            "List of advancement IDs that trigger the level clear.",
            "通关所指定的进度ID，可同时填写多个"
    })
    public static String[] triggerAdvancements = new String[]{"minecraft:nether/create_beacon", "minecraft:end/elytra"};

    // 聊天消息
    @Config.Comment({
            "The message displayed in the chat when the level is cleared.",
            "通关时聊天框显示的信息（<player>为通关者），如果留空则使用资源包的语言文件"
    })
    public static String chatMessage = "<player> 通关了这个存档";

    // 退出时删除存档
    @Config.Comment({
            "Whether to delete the world save when the server exits.",
            "是否在退出时删除存档"
    })
    public static boolean destroySaveOnExit = false;

    //服务器关闭后，删除存档的延迟时间
    @Config.Comment({
            "The delay time (in milliseconds) before deleting the world after the server shuts down.",
            "服务器关闭后，删除存档的延迟时间（毫秒）"
    })
    public static int deleteWorldDelayMillis = 3000;

    //删除存档时的重试次数
    @Config.Comment({
            "The number of retry attempts when deleting the world.",
            "删除存档时的重试次数"
    })
    public static int deleteWorldRetryAttempts = 10;

    //消息发送的时间间隔
    @Config.Comment({
            "The time interval (in milliseconds) between sending messages.",
            "消息发送的时间间隔（毫秒）"
    })
    public static int messageCooldown = 60000;

    //重试之间的延迟时间
    @Config.Comment({
            "The delay time (in milliseconds) between retry attempts.",
            "重试之间的延迟时间（毫秒）"
    })
    public static int retryDelayMillis = 500;

    // 是否显示多语言日志
    @Config.Comment({
            "Whether to show logs in multiple languages (Chinese and English).",
            "是否同时显示中英双语日志"
    })
    public static boolean showMultiLanguageLogs = false;

    // 最小触发玩家数
    @Config.Comment({
            "The minimum number of players required to trigger world deletion.",
            "触发存档删除的最小玩家人数"
    })
    public static int minPlayersToDestroySave = 1;
}