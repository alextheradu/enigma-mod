package com.alexradu.enigma.mixin;

import com.alexradu.enigma.EnigmaConfig;
import com.alexradu.enigma.EnigmaMod;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts sign updates to detect when a player writes the secret string.
 * We read the text at HEAD (from the packet) and act at RETURN (after vanilla
 * has committed the sign text to the block entity).
 */
@Mixin(ServerPlayNetworkHandler.class)
public class SignUpdateMixin {

    @Shadow public ServerPlayerEntity player;

    // Flags set at HEAD, consumed at RETURN
    @Unique private @Nullable BlockPos enigmaSignPos = null;
    @Unique private boolean enigmaSignFront = true;

    @Inject(method = "onUpdateSign", at = @At("HEAD"))
    private void enigma$checkSign(UpdateSignC2SPacket packet, CallbackInfo ci) {
        enigmaSignPos = null;

        var mod = EnigmaMod.getInstance();
        if (mod == null || !mod.getEnigmaConfig().isHuntActive()) return;

        String[] lines = packet.getText();
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line != null) sb.append(line);
        }
        if (sb.toString().trim().equalsIgnoreCase(mod.getEnigmaConfig().getSecretString())) {
            enigmaSignPos  = packet.getPos();
            enigmaSignFront = packet.isFront();
        }
    }

    @Inject(method = "onUpdateSign", at = @At("RETURN"))
    private void enigma$handleSecret(UpdateSignC2SPacket packet, CallbackInfo ci) {
        if (enigmaSignPos == null) return;

        var mod = EnigmaMod.getInstance();
        if (mod == null) return;

        // Run the reward command as the console
        String cmd = mod.getEnigmaConfig().getRewardCommand()
                .replace("%player%", player.getGameProfile().getName());
        try {
            player.getServer().getCommandManager().getDispatcher()
                    .execute(cmd, player.getServer().getCommandSource());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            EnigmaMod.LOGGER.error("Failed to run reward command: {}", e.getMessage());
        }

        var world = player.getServerWorld();
        var pos   = enigmaSignPos;

        if (mod.getEnigmaConfig().getSignAction() == EnigmaConfig.SignAction.BREAK) {
            world.breakBlock(pos, false);
        } else {
            // HIDE_TEXT — clear all lines on the correct side
            if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                SignText current = enigmaSignFront ? sign.getFrontText() : sign.getBackText();
                SignText cleared = current
                        .withMessage(0, Text.empty())
                        .withMessage(1, Text.empty())
                        .withMessage(2, Text.empty())
                        .withMessage(3, Text.empty());
                sign.setText(cleared, enigmaSignFront);
                sign.markDirty();
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        }

        enigmaSignPos = null;
    }
}
