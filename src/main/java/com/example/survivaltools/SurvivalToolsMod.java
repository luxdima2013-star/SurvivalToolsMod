package com.example.survivaltools;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import static net.minecraft.server.command.CommandManager.literal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class SurvivalToolsMod implements ModInitializer {
    private static final Map<UUID, Long> skillCooldowns = new HashMap<>();
    private static final Map<UUID, Long> kitCooldowns = new HashMap<>();
    private static final Map<UUID, Vec3d> homePositions = new HashMap<>();
    private static final Map<UUID, String> homeDimensions = new HashMap<>();

    private static class TeleportTask {
        long executeTime;
        Vec3d startPos;
        float startHealth;
        
        TeleportTask(long executeTime, Vec3d startPos, float startHealth) {
            this.executeTime = executeTime;
            this.startPos = startPos;
            this.startHealth = startHealth;
        }
    }

    private static final Map<UUID, TeleportTask> pendingTeleports = new HashMap<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Команда /skill
            dispatcher.register(literal("skill").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;
                long now = System.currentTimeMillis();
                long last = skillCooldowns.getOrDefault(player.getUuid(), 0L);
                if (now - last < 15 * 60 * 1000) {
                    long leftSec = (15 * 60 * 1000 - (now - last)) / 1000;
                    player.sendMessage(Text.of("§cКулдаун на /skill еще " + leftSec + " сек!"), false);
                    return 0;
                }
                skillCooldowns.put
                    
