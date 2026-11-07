package com.shumsic.autodrinkominousbottles;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.OminousBottleAmplifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AutoDrinkOminousBottlesClient implements ClientModInitializer {
    // --- 喝药配置 ---
    private static final boolean PULL_FROM_INVENTORY = true;
    private static final boolean PRIORITIZE_HIGHEST_LEVEL = true;
    private static final int HOLD_USE_TICKS = 45;
    private static final int SWAP_BACK_DELAY_TICKS = 3;
    private static final int COOLDOWN_TICKS = 0;
    
    // --- 时间常量（单位：tick，20 tick = 1秒）---
    private static final int RAID_GRACE_PERIOD_TICKS = 320; // 16秒，安全窗口开始
    private static final int RAID_MAX_WAIT_TICKS = 600;     // 30秒，安全窗口结束

    // --- 连点配置 ---
    // 🎯 间隔：80gt = 4秒（防止横扫打到还没脱离判定圈的怪）
    private static final int ATTACK_INTERVAL = 80; 

    // --- 状态机 ---
    private static boolean enabled = false; // 喝药开关 (R键)
    private static boolean attackEnabled = false; // 连点开关 (G键)

    // 喝药倒计时控制
    private int ticksToReleaseUse = -1;
    private int ticksToSwapBack = -1;
    private int prevHotbarSlot = -1;
    private boolean useWasForced = false;

    private long lastTriggerTick = -1000;
    private boolean firedThisVictory = false;
    private int emptyTicks = 0;
    private int triggerLockTicks = 0;

    private int debugTimer = 0;

    // 连点计时器
    private int attackTicker = 0;

    @Override
    public void onInitializeClient() {
        // R 键：控制自动喝药
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.auto_drink_ominous_bottles.toggle",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_R,
            KeyMapping.Category.MISC
        ));

        // G 键：控制专属连点
        KeyMapping attackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.auto_attack.toggle",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_G,
            KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean isZh = client.getLanguageManager().getSelected().startsWith("zh");

            // 处理 R 键（喝药切换）
            while (toggle.consumeClick()) {
                enabled = !enabled;
                if (client.player != null) {
                    updateModeMessage(client, isZh); 
                }
                firedThisVictory = false;
                emptyTicks = 0;
                triggerLockTicks = 0;
                useWasForced = false;
                ticksToReleaseUse = -1;
                ticksToSwapBack = -1;
                prevHotbarSlot = -1;
            }

            // 处理 G 键（连点切换）
            while (attackKey.consumeClick()) {
                attackEnabled = !attackEnabled;
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.literal(isZh ? "自动攻击: " + (attackEnabled ? "§a开启" : "§c关闭") : "Auto-Attack: " + (attackEnabled ? "§aEnabled" : "§cDisabled")));
                }
                if (!attackEnabled) {
                    client.options.keyAttack.setDown(false); // 关闭时确保松手
                }
                attackTicker = 0; // 重置计时器
            }

            // ==========================================
            // 🎯 优先级最高：如果开启了连点，直接挂起喝药逻辑
            // ==========================================
            if (attackEnabled) {
                handleAutoAttack(client, isZh);
                return; 
            }

            // ==========================================
            // 下方是常规喝药逻辑
            // ==========================================
            if (!enabled) return;
            if (client.player == null || client.level == null || client.gameMode == null) return;

            if (triggerLockTicks > 0) triggerLockTicks--;

            // 检测周围 100 格内是否有灾厄村民
            boolean hasRaidersNow = !client.level.getEntitiesOfClass(Raider.class, client.player.getBoundingBox().inflate(100.0)).isEmpty();

            if (hasRaidersNow) {
                emptyTicks = 0;
                firedThisVictory = false; 
                triggerLockTicks = 0; 
            } else {
                emptyTicks++;
            }

            // --- 心跳诊断（准星上方） ---
            if (debugTimer <= 0) {
                String status = hasRaidersNow 
                        ? (isZh ? "§a有 (正在打)" : "§aYes (Fighting)") 
                        : (isZh ? "§c无 (已清空 " + (emptyTicks / 20) + "s)" : "§cNone (Empty " + (emptyTicks / 20) + "s)");
                
                if (firedThisVictory) {
                    status += isZh ? " §e[已喝药, 停止监控]" : " §e[Drank, Paused]";
                }

                String prefix = isZh ? "[AutoDrink] 袭击成员: " : "[AutoDrink] Raiders: ";
                client.player.sendOverlayMessage(Component.literal(prefix + status));
                debugTimer = 10;
            } else {
                debugTimer--;
            }

            // --- 核心触发逻辑：16-30秒安全窗口 ---
            if (!hasRaidersNow && emptyTicks >= RAID_GRACE_PERIOD_TICKS && emptyTicks <= RAID_MAX_WAIT_TICKS && !firedThisVictory && triggerLockTicks == 0) {
                boolean hasBadOmen = client.player.hasEffect(MobEffects.BAD_OMEN);
                boolean hasRaidOmen = client.player.hasEffect(MobEffects.RAID_OMEN);
                
                if (!hasBadOmen && !hasRaidOmen) {
                    tryDrink(client, isZh);
                }
            }
            
            if (emptyTicks > RAID_MAX_WAIT_TICKS + 100) {
                firedThisVictory = false;
            }

            // --- 喝药动作控制 ---
            if (useWasForced) {
                if (ticksToReleaseUse <= 0) {
                    client.options.keyUse.setDown(false);
                    useWasForced = false;
                    ticksToSwapBack = SWAP_BACK_DELAY_TICKS;
                } else {
                    ticksToReleaseUse--;
                }
            } else if (ticksToSwapBack >= 0) {
                if (ticksToSwapBack == 0) {
                    if (prevHotbarSlot >= 0) {
                        client.player.getInventory().setSelectedSlot(prevHotbarSlot);
                        if (client.getConnection() != null) {
                            client.getConnection().send(new ServerboundSetCarriedItemPacket(prevHotbarSlot));
                        }
                    }
                    prevHotbarSlot = -1;
                    ticksToSwapBack = -1;
                } else {
                    ticksToSwapBack--;
                }
            }
        });
    }

    // 🌟 连点核心方法（反射强行解锁 private 方法）
    private void handleAutoAttack(Minecraft client, boolean isZh) {
        if (client.player == null || client.gameMode == null) return;

        // 计时器增加
        attackTicker++;
        if (attackTicker >= ATTACK_INTERVAL) {
            attackTicker = 0;
            
            try {
                // ✅ 核心修复：利用反射强行调用 26.3 中变成 private 的 startAttack 方法
                Method startAttackMethod = Minecraft.class.getDeclaredMethod("startAttack");
                startAttackMethod.setAccessible(true); // 破除 private 限制
                startAttackMethod.invoke(client);      // 执行攻击！
            } catch (Exception e) {
                // 反射失败（理论上不可能），直接忽略，防止编译报错
            }
            
            // 触发后立刻复位，防止卡住持续挖掘状态
            client.options.keyAttack.setDown(false);

            // 屏幕提示
            client.player.sendOverlayMessage(Component.literal(isZh ? "§e[AutoAttack] 触发连点!" : "§e[AutoAttack] Attack Triggered!"));
        }
    }

    private void updateModeMessage(Minecraft client, boolean isZh) {
        if (client.player == null) return;
        String msg = enabled 
                ? (isZh ? "自动不祥之兆: 已启用" : "Auto Ominous Bottles: Enabled")
                : (isZh ? "自动不祥之兆: 已禁用" : "Auto Ominous Bottles: Disabled");
        client.player.sendOverlayMessage(Component.literal(msg));

        if (enabled) {
            List<String> potionList = getPotionList(client, isZh);
            if (potionList.isEmpty()) {
                client.player.sendSystemMessage(Component.literal(isZh ? "§c[AutoDrink] 背包中未找到不祥之瓶。" : "§c[AutoDrink] No Ominous Bottles found."));
            } else {
                client.player.sendSystemMessage(Component.literal(isZh ? "§a[AutoDrink] 检测到以下药水：" : "§a[AutoDrink] Found Ominous Bottles:"));
                for (String info : potionList) {
                    client.player.sendSystemMessage(Component.literal("  - " + info));
                }
            }
        }
    }

    private List<String> getPotionList(Minecraft client, boolean isZh) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            ItemStack st = client.player.getInventory().getItem(i);
            if (isOminousBottle(st)) {
                int level = getOminousLevel(st);
                String location = (i < 9) ? (isZh ? "快捷栏" : "Hotbar") : (isZh ? "背包" : "Inventory");
                list.add(location + " - " + (isZh ? level + "级不祥之瓶" : "Level " + level + " Ominous Bottle"));
            }
        }
        return list;
    }

    private void tryDrink(Minecraft client, boolean isZh) {
        firedThisVictory = true; 
        triggerLockTicks = 60;

        long now = client.level.getGameTime();
        if (now - lastTriggerTick < COOLDOWN_TICKS) return;
        lastTriggerTick = now;

        int hotbarSlot = selectBottleHotbarSlot(client);

        if (hotbarSlot == -1 && PULL_FROM_INVENTORY) {
            int invSlotId = selectBottleInventorySlotId(client);
            if (invSlotId != -1) {
                int targetHotbar = firstEmptyHotbarSlot(client);
                if (targetHotbar == -1) targetHotbar = (client.player.getInventory().getSelectedSlot() + 1) % 9;
                try {
                    client.gameMode.handleContainerInput(
                        client.player.containerMenu.containerId,
                        invSlotId,
                        targetHotbar,
                        ContainerInput.SWAP,
                        client.player
                    );
                    hotbarSlot = targetHotbar;
                } catch (Throwable ignored) {
                    hotbarSlot = -1;
                }
            }
        }

        if (hotbarSlot == -1) {
            say(client, isZh ? "§c[AutoDrink] 自动喝药失败: 找不到不祥之瓶！" : "§c[AutoDrink] Failed: No Ominous Bottles!");
            return;
        }

        prevHotbarSlot = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(hotbarSlot);
        if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundSetCarriedItemPacket(hotbarSlot));
        }

        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.options.keyUse.setDown(true);
        useWasForced = true;
        ticksToReleaseUse = HOLD_USE_TICKS;

        say(client, isZh ? "§a[AutoDrink] 确认袭击结束，正在自动喝药！" : "§a[AutoDrink] Raid ended, drinking bottle!");
    }

    private int selectBottleHotbarSlot(Minecraft client) {
        if (!PRIORITIZE_HIGHEST_LEVEL) {
            for (int i = 0; i < 9; i++) {
                if (isOminousBottle(client.player.getInventory().getItem(i))) return i;
            }
            return -1;
        }
        int bestSlot = -1, bestLevel = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = client.player.getInventory().getItem(i);
            if (!isOminousBottle(st)) continue;
            int lvl = getOminousLevel(st);
            if (bestSlot == -1 || lvl > bestLevel) { bestSlot = i; bestLevel = lvl; }
        }
        return bestSlot;
    }

    private int selectBottleInventorySlotId(Minecraft client) {
        if (!PRIORITIZE_HIGHEST_LEVEL) {
            for (int slotId = 9; slotId <= 35; slotId++) {
                if (isOminousBottle(client.player.getInventory().getItem(slotId))) return slotId;
            }
            return -1;
        }
        int bestSlotId = -1, bestLevel = -1;
        for (int slotId = 9; slotId <= 35; slotId++) {
            ItemStack st = client.player.getInventory().getItem(slotId);
            if (!isOminousBottle(st)) continue;
            int lvl = getOminousLevel(st);
            if (bestSlotId == -1 || lvl > bestLevel) { bestSlotId = slotId; bestLevel = lvl; }
        }
        return bestSlotId;
    }

    private int firstEmptyHotbarSlot(Minecraft client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean isOminousBottle(ItemStack st) {
        return st != null && !st.isEmpty() && st.getItem() == Items.OMINOUS_BOTTLE;
    }

    private int getOminousLevel(ItemStack st) {
        try {
            OminousBottleAmplifier comp = st.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER);
            int amp = comp != null ? comp.value() : 0;
            return 1 + amp;
        } catch (Throwable ignored) { return 1; }
    }

    private void say(Minecraft client, String msg) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal(msg));
    }
}