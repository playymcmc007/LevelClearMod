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
    // public String triggerItemsDescription = "# 通关所指定的物品ID，可同时填写多个";

    @ConfigEntry.Gui.Tooltip
    public List<String> triggerAdvancements = new ArrayList<>(List.of("minecraft:nether/create_beacon","minecraft:end/elytra"));
    // public String triggerAdvancementsDescription = "# 通关所指定的进度ID，可同时填写多个";

    @ConfigEntry.Gui.Tooltip
    public String chatMessage = "<player> 通关了这个存档";
    // public String chatMessageDescription = "# 通关时聊天框显示的信息（<player>为通关者）";

    @ConfigEntry.Gui.Tooltip
    public boolean destroySaveOnExit = false;
    // public String destroySaveOnExitDescription = "# 是否启用存档销毁选项，若为true，存档将在退出游戏后删除";

    @ConfigEntry.Gui.Tooltip
    public int minPlayersToDestroySave = 1;
    // public int minPlayersToDestroySaveDescription = "# 触发存档销毁的最小玩家人数";
}
