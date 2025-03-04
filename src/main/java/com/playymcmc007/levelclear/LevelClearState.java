package com.playymcmc007.levelclear;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.ArrayList;
import java.util.List;

public class LevelClearState extends PersistentState {
    private boolean triggered = false;
    private boolean shouldDestroySave = false;
    private final List<String> triggeredPlayers = new ArrayList<>();


    public static LevelClearState getServerState(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();

        return manager.getOrCreate(getType(), "levelclear_state");
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

        NbtList playersList = new NbtList();
        for (String player : triggeredPlayers) {
            playersList.add(NbtString.of(player));
        }
        tag.put("triggeredPlayers", playersList);

        return tag;
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