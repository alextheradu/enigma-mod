package com.alexradu.enigma;

import com.alexradu.enigma.commands.EnigmaCommand;
import com.alexradu.enigma.handlers.DropHandler;
import com.alexradu.enigma.handlers.InteractHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnigmaMod implements ModInitializer {

    public static final String MOD_ID = "enigma";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static EnigmaMod instance;

    private EnigmaConfig enigmaConfig;
    private ClueItemFactory clueItemFactory;
    private PlayerDataManager playerDataManager;

    @Override
    public void onInitialize() {
        instance = this;

        enigmaConfig = new EnigmaConfig();
        clueItemFactory = new ClueItemFactory(this);
        playerDataManager = new PlayerDataManager(this);

        DropHandler.register(this);
        InteractHandler.register(this);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EnigmaCommand.register(dispatcher, this));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> playerDataManager.save());

        LOGGER.info("Enigma mod enabled!");
    }

    public static EnigmaMod getInstance() { return instance; }
    public EnigmaConfig getEnigmaConfig() { return enigmaConfig; }
    public ClueItemFactory getClueItemFactory() { return clueItemFactory; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
}
