package com.alexradu.enigma.handlers;

import com.alexradu.enigma.EnigmaMod;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Random;
import java.util.stream.IntStream;

public class DropHandler {

    private static final Random RANDOM = new Random();

    public static void register(EnigmaMod mod) {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (!mod.getEnigmaConfig().isHuntActive()) return;

            var killedType = killed.getType();
            String killedId = Registries.ENTITY_TYPE.getId(killedType).toString();

            for (var entry : mod.getEnigmaConfig().getMobDrops()) {
                if (entry.type().equalsIgnoreCase(killedId)) {
                    if (RANDOM.nextDouble() * 100.0 < entry.chance()) {
                        var hints = mod.getEnigmaConfig().getHints();
                        if (hints.isEmpty()) return;

                        ServerPlayerEntity player = killer instanceof ServerPlayerEntity
                                ? (ServerPlayerEntity) killer : null;

                        var candidates = player != null
                                ? mod.getPlayerDataManager().getUnreceivedIndices(player.getUuid(), hints.size())
                                : new ArrayList<>(IntStream.range(0, hints.size()).boxed().toList());

                        if (candidates.isEmpty()) return;

                        int index = candidates.get(RANDOM.nextInt(candidates.size()));
                        // In 1.20.1, dropStack takes only the ItemStack (no world param)
                        killed.dropStack(mod.getClueItemFactory().createClueItem(hints.get(index), index));

                        if (player != null) {
                            mod.getPlayerDataManager().markReceived(player.getUuid(), index);
                        }
                    }
                    break;
                }
            }
        });
    }
}
