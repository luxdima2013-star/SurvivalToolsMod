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

import java.util.*;

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
                skillCooldowns.put(player.getUuid(), now);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30 * 20, 6)); // Сопротивление 7
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30 * 20, 0));       // Скорость 1
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 30 * 20, 1));    // Сила 2
                player.sendMessage(Text.of("§aСкиллы активированы на 30 секунд!"), false);
                return 1;
            }));

            // Команда /kit start
            dispatcher.register(literal("kit").then(literal("start").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;
                long now = System.currentTimeMillis();
                long last = kitCooldowns.getOrDefault(player.getUuid(), 0L);
                if (now - last < 60 * 60 * 1000) {
                    long leftMin = (60 * 60 * 1000 - (now - last)) / (60 * 1000);
                    player.sendMessage(Text.of("§cКулдаун на /kit start еще " + leftMin + " мин!"), false);
                    return 0;
                }
                kitCooldowns.put(player.getUuid(), now);
                
                player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                player.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));

                player.getInventory().insertStack(new ItemStack(Items.IRON_SWORD));
                player.getInventory().insertStack(new ItemStack(Items.IRON_PICKAXE));
                player.getInventory().insertStack(new ItemStack(Items.COOKED_BEEF, 32));
                
                player.sendMessage(Text.of("§aСтартовый кит успешно получен!"), false);
                return 1;
            })));

            // Команда /sethome
            dispatcher.register(literal("sethome").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;
                homePositions.put(player.getUuid(), player.getPos());
                homeDimensions.put(player.getUuid(), player.getWorld().getRegistryKey().getValue().toString());
                player.sendMessage(Text.of("§aТочка дома успешно сохранена!"), false);
                return 1;
            }));

            // Команда /home
            dispatcher.register(literal("home").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;
                if (!homePositions.containsKey(player.getUuid())) {
                    player.sendMessage(Text.of("§cУ вас не установлена точка дома! Используйте /sethome"), false);
                    return 0;
                }
                player.sendMessage(Text.of("§eТелепортация через 3 секунды. Не двигайтесь и не получайте урон!"), false);
                pendingTeleports.put(player.getUuid(), new TeleportTask(
                    System.currentTimeMillis() + 3000,
                    player.getPos(),
                    player.getHealth()
                ));
                return 1;
            }));
        });

        // Проверка таймера телепортации и отмены при движении/уроне
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            Iterator> iterator = pendingTeleports.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, TeleportTask> entry = iterator.next();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player == null) {
                    iterator.remove();
                    continue;
                }
                TeleportTask task = entry.getValue();
                
                // Проверка движения (>0.2 блоков) или урона
                if (player.getPos().squaredDistanceTo(task.startPos) > 0.04 || player.getHealth() < task.startHealth) {
                    player.sendMessage(Text.of("§cТелепортация отменена из-за движения или урона!"), false);
                    iterator.remove();
                    continue;
                }

                if (now >= task.executeTime) {
                    Vec3d targetPos = homePositions.get(player.getUuid());
                    if (targetPos != null) {
                        player.teleport(targetPos.x, targetPos.y, targetPos.z, true);
                        player.sendMessage(Text.of("§aВы телепортировались домой!"), false);
                    }
                    iterator.remove();
                }
            }
        });
    }
                }
