package com.playymcmc007.levelclear;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import java.util.ArrayList;
import java.util.List;

public class LevelClearState extends WorldSavedData {
    private boolean triggered = false;
    private boolean shouldDestroySave = false;
    private final List<String> triggeredPlayers = new ArrayList<>();

    public LevelClearState(String name) {
        super(name);
    }

    public static LevelClearState get(World world) {
        WorldSavedData data = world.getMapStorage().getOrLoadData(LevelClearState.class, "levelclear_state");
        if (data == null) {
            data = new LevelClearState("levelclear_state");
            world.getMapStorage().setData("levelclear_state", data);
        }
        return (LevelClearState) data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        triggered = nbt.getBoolean("triggered");
        shouldDestroySave = nbt.getBoolean("shouldDestroySave");

        NBTTagList playersList = nbt.getTagList("triggeredPlayers", 8); // 8表示字符串类型
        triggeredPlayers.clear();
        for (int i = 0; i < playersList.tagCount(); i++) {
            triggeredPlayers.add(playersList.getStringTagAt(i));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("triggered", triggered);
        nbt.setBoolean("shouldDestroySave", shouldDestroySave);

        NBTTagList playersList = new NBTTagList();
        for (String player : triggeredPlayers) {
            playersList.appendTag(new net.minecraft.nbt.NBTTagString(player));
        }
        nbt.setTag("triggeredPlayers", playersList);

        return nbt;
    }

    public void addTriggeredPlayer(String playerName) {
        if (!triggeredPlayers.contains(playerName)) {
            triggeredPlayers.add(playerName);
            markDirty();
        }
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
        markDirty();
    }

    public List<String> getTriggeredPlayers() {
        return new ArrayList<>(triggeredPlayers);
    }
}