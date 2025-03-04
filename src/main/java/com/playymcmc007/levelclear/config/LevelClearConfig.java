package com.playymcmc007.levelclear.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

@Config(name = "levelclear")
public class LevelClearConfig implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public List<String> triggerItems = new ArrayList<>(List.of("minecraft:elytra", "minecraft:nether_star"));
    //通关所指定的物品ID，可同时填写多个

    @ConfigEntry.Gui.Tooltip
    public List<String> triggerAdvancements = new ArrayList<>(List.of("minecraft:nether/create_beacon","minecraft:end/elytra"));
    //通关所指定的进度ID，可同时填写多个

    @ConfigEntry.Gui.Tooltip
    public String chatMessage = "";
    //通关时聊天框显示的信息（<player>为通关者），如果留空则使用json语言文件

    @ConfigEntry.Gui.Tooltip
    public boolean destroySaveOnExit = false;
    //是否启用存档销毁选项，若为true，存档将在退出游戏后删除

    @ConfigEntry.Gui.Tooltip
    public int minPlayersToDestroySave = 1;
    //触发存档销毁的最小玩家人数

    @ConfigEntry.Gui.Tooltip
    public int messageCooldown = 60000;
    //消息发送的时间间隔（毫秒）

    @ConfigEntry.Gui.Tooltip
    public int deleteWorldRetryAttempts = 3;
    //删除存档时的重试次数

    @ConfigEntry.Gui.Tooltip
    public int retryDelayMillis = 500;
    //重试之间的延迟时间（以毫秒为单位）

    @ConfigEntry.Gui.Tooltip
    public boolean showMultiLanguageLogs = false;
    //（程序员用）是否输出中英双语后台日志
}
