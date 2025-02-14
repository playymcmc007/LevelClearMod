package com.playymcmc007.levelclear;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class LevelClearState extends PersistentState {
    private boolean triggered = false;
    private boolean shouldDestroySave = false;
    private List<String> triggeredPlayers = new ArrayList<>();
    private File triggeredPlayersFile;


    public static LevelClearState getServerState(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        LevelClearState state = manager.getOrCreate(getType(), "levelclear_state");
        // 初始化文件路径
        File worldSavePath = server.getSavePath(WorldSavePath.ROOT).toFile();
        state.setWorldSavePath(worldSavePath);

        // 加载玩家名字
        state.loadTriggeredPlayersFromFile();

        return state;
    }

    public static Type<LevelClearState> getType() {
        return new Type<>(
                LevelClearState::new,
                (nbt, registryLookup) -> fromNbt(nbt),
                null
        );
    }

    public static LevelClearState fromNbt(NbtCompound tag) {
        LevelClearState state = new LevelClearState();
        state.triggered = tag.getBoolean("triggered");
        state.shouldDestroySave = tag.getBoolean("shouldDestroySave");
        // 读取触发玩家名字列表
        NbtList playersList = tag.getList("triggeredPlayers", NbtElement.STRING_TYPE);
        for (NbtElement element : playersList) {
            state.triggeredPlayers.add(element.asString());
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean("triggered", triggered);
        tag.putBoolean("shouldDestroySave", shouldDestroySave);

        // 保存触发玩家名字列表到 NBT
        NbtList playersList = new NbtList();
        for (String player : triggeredPlayers) {
            playersList.add(NbtString.of(player));
        }
        tag.put("triggeredPlayers", playersList);

        return tag; // 确保 return 语句在最后
    }
    //从77行到111行是屎山代码，最好别动……
    public void setWorldSavePath(File worldSavePath) {
        this.triggeredPlayersFile = new File(worldSavePath, "data/levelclear_triggered_players.dat");
        // 确保 data 目录存在
        File dataDir = new File(worldSavePath, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }
    public synchronized void saveTriggeredPlayersToFile() {
        try (FileOutputStream fos = new FileOutputStream(triggeredPlayersFile);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(triggeredPlayers);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public synchronized void loadTriggeredPlayersFromFile() {
        if (!triggeredPlayersFile.exists()) return;

        try (FileInputStream fis = new FileInputStream(triggeredPlayersFile);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            triggeredPlayers = (List<String>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    public void addTriggeredPlayer(String playerName) {
        if (!triggeredPlayers.contains(playerName)) {
            triggeredPlayers.add(playerName);
            markDirty(); // 标记状态需要保存

            // 异步保存到文件
            new Thread(this::saveTriggeredPlayersToFile).start();
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