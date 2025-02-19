package com.playymcmc007.levelclear;

import com.google.gson.Gson;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LevelClearState extends PersistentState {
    private boolean triggered = false;
    private boolean shouldDestroySave = false;
    private List<String> triggeredPlayers = new ArrayList<>();
    private File triggeredPlayersFile;
    private static final Logger LOGGER = LoggerFactory.getLogger(LevelClearState.class);


    public static LevelClearState getServerState(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        LevelClearState state = manager.getOrCreate(getType(), "levelclear_state");

        File worldSavePath = server.getSavePath(WorldSavePath.ROOT).toFile();
        state.setWorldSavePath(worldSavePath);

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
        this.triggeredPlayersFile = new File(worldSavePath, "data/levelclear_triggered_players.json");
        File dataDir = new File(worldSavePath, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    public synchronized void saveTriggeredPlayersToFile() {
        try (FileOutputStream fos = new FileOutputStream(triggeredPlayersFile)) {
            fos.write(new Gson().toJson(triggeredPlayers).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("保存失败! ", e);
        }
    }

    public synchronized void loadTriggeredPlayersFromFile() {
        if (!triggeredPlayersFile.exists()) return;

        try (FileInputStream fis = new FileInputStream(triggeredPlayersFile)) {
            triggeredPlayers = (List<String>) new Gson().fromJson(new InputStreamReader(fis, StandardCharsets.UTF_8), List.class);
        } catch (IOException e) {
            LOGGER.error("加载失败! ", e);
        }
    }

    public void addTriggeredPlayer(String playerName) {
        if (!triggeredPlayers.contains(playerName)) {
            triggeredPlayers.add(playerName);
            markDirty();
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