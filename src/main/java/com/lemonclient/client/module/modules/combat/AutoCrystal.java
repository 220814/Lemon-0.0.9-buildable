package com.lemonclient.client.module.modules.combat;

import com.lemonclient.api.event.Phase;
import com.lemonclient.api.event.events.EntityRemovedEvent;
import com.lemonclient.api.event.events.OnUpdateWalkingPlayerEvent;
import com.lemonclient.api.event.events.PacketEvent;
import com.lemonclient.api.event.events.RenderEvent;
import com.lemonclient.api.setting.values.BooleanSetting;
import com.lemonclient.api.setting.values.ColorSetting;
import com.lemonclient.api.setting.values.DoubleSetting;
import com.lemonclient.api.setting.values.IntegerSetting;
import com.lemonclient.api.setting.values.ModeSetting;
import com.lemonclient.api.util.misc.CrystalUtil;
import com.lemonclient.api.util.misc.Timing;
import com.lemonclient.api.util.player.BurrowUtil;
import com.lemonclient.api.util.player.Locks;
import com.lemonclient.api.util.player.PlayerPacket;
import com.lemonclient.api.util.player.PlayerUtil;
import com.lemonclient.api.util.player.PredictUtil;
import com.lemonclient.api.util.player.RotationUtil;
import com.lemonclient.api.util.render.GSColor;
import com.lemonclient.api.util.render.RenderUtil;
import com.lemonclient.api.util.world.BlockUtil;
import com.lemonclient.api.util.world.EntityUtil;
import com.lemonclient.api.util.world.combat.DamageUtil;
import com.lemonclient.client.LemonClient;
import com.lemonclient.client.manager.managers.PlayerPacketManager;
import com.lemonclient.client.module.Category;
import com.lemonclient.client.module.Module;
import com.lemonclient.client.module.ModuleManager;
import com.lemonclient.client.module.modules.dev.OffHand;
import com.lemonclient.client.module.modules.dev.PistonAura;
import com.lemonclient.client.module.modules.dev.PullCrystal;
import com.lemonclient.client.module.modules.qwq.AutoEz;
import com.lemonclient.mixin.mixins.accessor.AccessorCPacketUseEntity;
import com.mojang.realmsclient.gui.ChatFormatting;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockObsidian;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityExpBottle;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketClickWindow;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.network.play.client.CPacketPlayer.PositionRotation;
import net.minecraft.network.play.client.CPacketPlayer.Rotation;
import net.minecraft.network.play.client.CPacketUseEntity.Action;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.network.play.server.SPacketSpawnObject;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

@Module.Declaration(name = "AutoCrystal", category = Category.Combat, priority = 999)
public class AutoCrystal extends Module {
   public static CopyOnWriteArrayList<CPacketUseEntity> packetList = new CopyOnWriteArrayList<>();
   public static AutoCrystal INSTANCE = new AutoCrystal();
   ModeSetting page = this.registerMode("Page", Arrays.asList("General", "Place", "Break", "Combat", "Switch", "Base", "Predict", "Dev", "Render"), "General");
   ModeSetting logic = this.registerMode("Logic", Arrays.asList("PlaceBreak", "BreakPlace"), "BreakPlace", () -> this.page.getValue().equals("General"));
   IntegerSetting updateDelay = this.registerInteger("CalcDelay", 25, 0, 1000, () -> this.page.getValue().equals("General"));
   BooleanSetting wall = this.registerBoolean("WallCheck", true, () -> this.page.getValue().equals("General"));
   BooleanSetting wallAI = this.registerBoolean("WallAI", true, () -> this.wall.getValue() && this.page.getValue().equals("General"));
   IntegerSetting enemyRange = this.registerInteger("EnemyRange", 7, 1, 16, () -> this.page.getValue().equals("General"));
   IntegerSetting maxTarget = this.registerInteger("MaxTargets", 1, 1, 10, () -> this.page.getValue().equals("General"));
   BooleanSetting highVersion = this.registerBoolean("1.13", false, () -> this.page.getValue().equals("General"));
   ModeSetting godMode = this.registerMode("SelfDamage", Arrays.asList("Auto", "GodMode", "NoGodMode"), "Auto", () -> this.page.getValue().equals("General"));
   DoubleSetting maxSelfDMG = this.registerDouble(
      "MaxSelfDmg", 12.0, 0.0, 36.0, () -> !this.godMode.getValue().equals("GodMode") && this.page.getValue().equals("General")
   );
   DoubleSetting balance = this.registerDouble(
      "HealthBalance", 1.5, 0.0, 10.0, () -> !this.godMode.getValue().equals("GodMode") && this.page.getValue().equals("General")
   );
   BooleanSetting eat = this.registerBoolean("WhileEating", true, () -> this.page.getValue().equals("General"));
   BooleanSetting place = this.registerBoolean("Place", true, () -> this.page.getValue().equals("Place"));
   BooleanSetting multiPlace = this.registerBoolean("MultiPlace", false, () -> this.place.getValue() && this.page.getValue().equals("Place"));
   BooleanSetting packet = this.registerBoolean("PacketCrystal", true, () -> this.place.getValue() && this.page.getValue().equals("Place"));
   IntegerSetting placeDelay = this.registerInteger("PlaceDelay", 50, 0, 1000, () -> this.place.getValue() && this.page.getValue().equals("Place"));
   DoubleSetting placeRange = this.registerDouble("PlaceRange", 5.5, 0.0, 6.0, () -> this.place.getValue() && this.page.getValue().equals("Place"));
   DoubleSetting placeWallRange = this.registerDouble(
      "PlaceWallRange", 3.0, 0.1, 6.0, () -> this.place.getValue() && this.wall.getValue() && !this.wallAI.getValue() && this.page.getValue().equals("Place")
   );
   DoubleSetting minDamage = this.registerDouble("MinDmg", 4.0, 0.0, 36.0, () -> this.place.getValue() && this.page.getValue().equals("Place"));
   BooleanSetting forcePlace = this.registerBoolean(
      "OverridePlace", false, () -> !this.godMode.getValue().equals("GodMode") && this.place.getValue() && this.page.getValue().equals("Place")
   );
   BooleanSetting crystalCheck = this.registerBoolean("CrystalCheck", false, () -> this.place.getValue() && this.page.getValue().equals("Place"));
   BooleanSetting placeAfter = this.registerBoolean("PlaceAfterBreak", true, () -> this.place.getValue() && this.page.getValue().equals("Place"));
   BooleanSetting post = this.registerBoolean("Posted", true, () -> this.place.getValue() && this.placeAfter.getValue() && this.page.getValue().equals("Place"));
   BooleanSetting placeOnRemove = this.registerBoolean("PlaceOnRemove", true, () -> this.place.getValue() && this.page.getValue().equals("Place"));
   BooleanSetting explode = this.registerBoolean("Break", true, () -> this.page.getValue().equals("Break"));
   BooleanSetting PacketExplode = this.registerBoolean("PacketExplode", true, () -> this.explode.getValue() && this.page.getValue().equals("Break"));
   IntegerSetting hitDelay = this.registerInteger("BreakDelay", 50, 0, 1000, () -> this.explode.getValue() && this.page.getValue().equals("Break"));
   IntegerSetting PacketExplodeDelay = this.registerInteger(
      "PacketExplodeDelay", 45, 0, 500, () -> this.PacketExplode.getValue() && this.page.getValue().equals("Break")
   );
   DoubleSetting breakRange = this.registerDouble("BreakRange", 5.5, 0.0, 6.0, () -> this.explode.getValue() && this.page.getValue().equals("Break"));
   DoubleSetting breakWallRange = this.registerDouble(
      "BreakWallRange", 3.0, 0.1, 6.0, () -> this.explode.getValue() && this.wall.getValue() && !this.wallAI.getValue() && this.page.getValue().equals("Break")
   );
   IntegerSetting breakMinDmg = this.registerInteger("BreakMinDmg", 2, 0, 36, () -> this.explode.getValue() && this.page.getValue().equals("Break"));
   BooleanSetting forceBreak = this.registerBoolean(
      "OverrideBreak", false, () -> !this.godMode.getValue().equals("GodMode") && this.explode.getValue() && this.page.getValue().equals("Break")
   );
   BooleanSetting antiWeakness = this.registerBoolean("AntiWeakness", false, () -> this.explode.getValue() && this.page.getValue().equals("Break"));
   ModeSetting antiWeakMode = this.registerMode(
      "SwitchMode",
      Arrays.asList("Normal", "Silent", "Bypass"),
      "Normal",
      () -> this.explode.getValue() && this.antiWeakness.getValue() && this.page.getValue().equals("Break")
   );
   BooleanSetting PredictHit = this.registerBoolean("PredictHit", false, () -> this.explode.getValue() && this.page.getValue().equals("Break"));
   IntegerSetting PredictHitFactor = this.registerInteger(
      "PredictHitFactor", 2, 1, 20, () -> this.explode.getValue() && this.PredictHit.getValue() && this.page.getValue().equals("Break")
   );
   BooleanSetting rotate = this.registerBoolean("Rotate", true, () -> this.page.getValue().equals("Combat"));
   BooleanSetting swing = this.registerBoolean("Swing", true, () -> this.page.getValue().equals("Combat"));
   BooleanSetting packetSwing = this.registerBoolean("PacketSwing", false, () -> this.swing.getValue() && this.page.getValue().equals("Combat"));
   BooleanSetting facePlace = this.registerBoolean("FacePlace", true, () -> this.page.getValue().equals("Combat"));
   IntegerSetting BlastHealth = this.registerInteger("BlastHealth", 10, 0, 20, () -> this.facePlace.getValue() && this.page.getValue().equals("Combat"));
   IntegerSetting armorCount = this.registerInteger("ArmorCount", 1, 0, 64, () -> this.facePlace.getValue() && this.page.getValue().equals("Combat"));
   IntegerSetting armorRate = this.registerInteger(
      "ArmorDamage", 15, 0, 100, () -> this.facePlace.getValue() && this.armorCount.getValue() > 0 && this.page.getValue().equals("Combat")
   );
   DoubleSetting fpMinDmg = this.registerDouble("FpMinDmg", 1.0, 0.0, 36.0, () -> this.facePlace.getValue() && this.page.getValue().equals("Combat"));
   BooleanSetting ClientSide = this.registerBoolean("ClientSide", false, () -> this.page.getValue().equals("Combat"));
   BooleanSetting autoSwitch = this.registerBoolean("AutoSwitch", true, () -> this.page.getValue().equals("Switch"));
   BooleanSetting offhand = this.registerBoolean("Offhand", false, () -> this.autoSwitch.getValue() && this.page.getValue().equals("Switch"));
   BooleanSetting switchBack = this.registerBoolean(
      "SwitchBack", true, () -> this.autoSwitch.getValue() && !this.offhand.getValue() && this.page.getValue().equals("Switch")
   );
   BooleanSetting bypass = this.registerBoolean(
      "Bypass", false, () -> this.autoSwitch.getValue() && !this.offhand.getValue() && this.switchBack.getValue() && this.page.getValue().equals("Switch")
   );
   BooleanSetting packetSwitch = this.registerBoolean(
      "PacketSwitch",
      false,
      () -> this.autoSwitch.getValue() && !this.offhand.getValue() && this.switchBack.getValue() && this.page.getValue().equals("Switch")
   );
   BooleanSetting forceUpdate = this.registerBoolean(
      "ForceUpdate",
      false,
      () -> this.autoSwitch.getValue()
         && !this.offhand.getValue()
         && this.switchBack.getValue()
         && this.bypass.getValue()
         && this.page.getValue().equals("Switch")
   );
   BooleanSetting base = this.registerBoolean("Base", false, () -> this.page.getValue().equals("Base"));
   IntegerSetting baseDelay = this.registerInteger("BaseDelay", 100, 0, 200, () -> this.base.getValue() && this.page.getValue().equals("Base"));
   IntegerSetting toggleDamage = this.registerInteger("ToggleMaxDmg", 12, 0, 36, () -> this.base.getValue() && this.page.getValue().equals("Base"));
   IntegerSetting baseMinDamage = this.registerInteger("BaseMinDmg", 6, 0, 36, () -> this.base.getValue() && this.page.getValue().equals("Base"));
   DoubleSetting maxSpeed = this.registerDouble("MaxSpeed", 10.0, 0.0, 50.0, () -> this.base.getValue() && this.page.getValue().equals("Base"));
   BooleanSetting baseBypass = this.registerBoolean("BaseBypassSwitch", false, () -> this.base.getValue() && this.page.getValue().equals("Base"));
   BooleanSetting packetPlace = this.registerBoolean("PacketPlace", false, () -> this.base.getValue() && this.page.getValue().equals("Base"));
   BooleanSetting target = this.registerBoolean("Target", true, () -> this.page.getValue().equals("Predict"));
   BooleanSetting self = this.registerBoolean("Self", true, () -> this.page.getValue().equals("Predict"));
   IntegerSetting tickPredict = this.registerInteger("TickPredict", 8, 0, 30, () -> this.page.getValue().equals("Predict"));
   BooleanSetting calculateYPredict = this.registerBoolean("CalculateYPredict", true, () -> this.page.getValue().equals("Predict"));
   IntegerSetting startDecrease = this.registerInteger(
      "StartDecrease", 39, 0, 200, () -> this.page.getValue().equals("Predict") && this.calculateYPredict.getValue()
   );
   IntegerSetting exponentStartDecrease = this.registerInteger(
      "ExponentStart", 2, 1, 5, () -> this.page.getValue().equals("Predict") && this.calculateYPredict.getValue()
   );
   IntegerSetting decreaseY = this.registerInteger("DecreaseY", 2, 1, 5, () -> this.page.getValue().equals("Predict") && this.calculateYPredict.getValue());
   IntegerSetting exponentDecreaseY = this.registerInteger(
      "ExponentDecreaseY", 1, 1, 3, () -> this.page.getValue().equals("Predict") && this.calculateYPredict.getValue()
   );
   BooleanSetting splitXZ = this.registerBoolean("SplitXZ", true, () -> this.page.getValue().equals("Predict"));
   BooleanSetting manualOutHole = this.registerBoolean("ManualOutHole", false, () -> this.page.getValue().equals("Predict"));
   BooleanSetting aboveHoleManual = this.registerBoolean(
      "AboveHoleManual", false, () -> this.page.getValue().equals("Predict") && this.manualOutHole.getValue()
   );
   BooleanSetting stairPredict = this.registerBoolean("StairPredict", false, () -> this.page.getValue().equals("Predict"));
   IntegerSetting nStair = this.registerInteger("NStair", 2, 1, 4, () -> this.page.getValue().equals("Predict") && this.stairPredict.getValue());
   DoubleSetting speedActivationStair = this.registerDouble(
      "SpeedActivationStair", 0.11, 0.0, 1.0, () -> this.page.getValue().equals("Predict") && this.stairPredict.getValue()
   );
   IntegerSetting cooldown = this.registerInteger("Cooldown", 500, 0, 2000, () -> this.page.getValue().equals("Dev"));
   BooleanSetting MineDetect = this.registerBoolean("MineDetect", false, () -> this.page.getValue().equals("Dev"));
   public BooleanSetting civ = this.registerBoolean("AllowCiv", false, () -> this.MineDetect.getValue() && this.page.getValue().equals("Dev"));
   public BooleanSetting rangeCheck = this.registerBoolean("RangeCheck", false, () -> this.MineDetect.getValue() && this.page.getValue().equals("Dev"));
   BooleanSetting packetOptimize = this.registerBoolean("PacketOptimize", true, () -> this.page.getValue().equals("Dev"));
   IntegerSetting limit = this.registerInteger("Limit", 40, 1, 100, () -> this.packetOptimize.getValue() && this.page.getValue().equals("Dev"));
   BooleanSetting pause = this.registerBoolean("PausePistonAura", true, () -> this.page.getValue().equals("Dev"));
   BooleanSetting showBreakDelay = this.registerBoolean("ShowBreakDelay", true, () -> this.page.getValue().equals("Dev"));
   BooleanSetting speedDebug = this.registerBoolean("SpeedDebug", true, () -> this.page.getValue().equals("Dev"));
   ModeSetting mode = this.registerMode("Mode", Arrays.asList("Solid", "Both", "Outline"), "Both", () -> this.page.getValue().equals("Render"));
   BooleanSetting showDamage = this.registerBoolean("ShowDamage", false, () -> this.page.getValue().equals("Render"));
   BooleanSetting showSelfDamage = this.registerBoolean("ShowSelfDamage", false, () -> this.showDamage.getValue() && this.page.getValue().equals("Render"));
   BooleanSetting flat = this.registerBoolean("Flat", false, () -> this.page.getValue().equals("Render"));
   IntegerSetting width = this.registerInteger("Width", 1, 0, 10, () -> this.page.getValue().equals("Render"));
   ColorSetting color = this.registerColor("Color", new GSColor(255, 255, 255), () -> this.page.getValue().equals("Render"));
   IntegerSetting alpha = this.registerInteger("Alpha", 50, 0, 255, () -> this.page.getValue().equals("Render"));
   IntegerSetting outAlpha = this.registerInteger("OutlineAlpha", 125, 0, 255, () -> this.page.getValue().equals("Render"));
   IntegerSetting movingTime = this.registerInteger("MovingTime", 0, 0, 500, () -> this.page.getValue().equals("Render"));
   IntegerSetting lifeTime = this.registerInteger("FadeTime", 100, 0, 500, () -> this.page.getValue().equals("Render"));
   BooleanSetting scale = this.registerBoolean("Scale", false, () -> this.page.getValue().equals("Render"));
   PredictUtil.PredictSettings settings;
   Timing PacketExplodeTimer = new Timing();
   Timing ExplodeTimer = new Timing();
   Timing UpdateTimer = new Timing();
   Timing PlaceTimer = new Timing();
   Timing CalcTimer = new Timing();
   Timing cooldownTimer = new Timing();
   EntityEnderCrystal lastCrystal;
   EntityEnderCrystal crystal;
   Vec3d movingPlaceNow = new Vec3d(-1.0, -1.0, -1.0);
   BlockPos lastBestPlace = null;
   AutoCrystal.PlaceInfo placeInfo;
   boolean ShouldInfoLastBreak = false;
   boolean afterAttacking = false;
   boolean canPredictHit = false;
   boolean calculated;
   boolean canBase;
   long infoBreakTime = 0L;
   long lastBreakTime = 0L;
   long updateTime;
   long startTime;
   int lastEntityID = -1;
   int placements = 0;
   int StuckTimes = 0;
   int crystals = 0;
   int waited;
   int crystalSlot;
   int crystalId;
   int lastSlot;
   Vec3d lastHitVec;
   @EventHandler
   private final Listener<OnUpdateWalkingPlayerEvent> onUpdateWalkingPlayerEventListener = new Listener<>(event -> {
      if (event.getPhase() == Phase.PRE && this.lastHitVec != null && this.rotate.getValue()) {
         PlayerPacket packet = new PlayerPacket(this, RotationUtil.getRotationTo(this.lastHitVec));
         PlayerPacketManager.INSTANCE.addPacket(packet);
      }
   });
   @EventHandler
   private final Listener<PacketEvent.PostSend> postSendListener = new Listener<>(
      event -> {
         if (mc.world != null && mc.player != null && !mc.player.isDead) {
            if (event.getPacket() instanceof CPacketUseEntity
               && this.placeAfter.getValue()
               && this.post.getValue()
               && ((CPacketUseEntity)event.getPacket()).getAction() == Action.ATTACK) {
               Entity attacked = ((CPacketUseEntity)event.getPacket()).getEntityFromWorld(mc.world);
               if (attacked instanceof EntityEnderCrystal) {
                  long passed = this.PlaceTimer.getTime();
                  this.PlaceTimer.setMs(this.placeDelay.getValue() + 1);
                  this.place(false);
                  this.PlaceTimer.setTime(passed);
               }
            }
         }
      }
   );
   @EventHandler
   private final Listener<PacketEvent.PostReceive> postReceiveListener = new Listener<>(
      event -> {
         if (mc.world != null && mc.player != null && !mc.player.isDead) {
            if (event.getPacket() instanceof SPacketSpawnObject) {
               SPacketSpawnObject packet = (SPacketSpawnObject)event.getPacket();
               if (this.PredictHit.getValue()) {
                  for (Entity e : mc.world.loadedEntityList) {
                     if ((
                           e instanceof EntityItem
                              || e instanceof EntityArrow
                              || e instanceof EntityEnderPearl
                              || e instanceof EntitySnowball
                              || e instanceof EntityEgg
                        )
                        && e.getDistance(packet.getX(), packet.getY(), packet.getZ()) <= 6.0) {
                        this.lastEntityID = -1;
                        this.canPredictHit = false;
                        event.cancel();
                     }
                  }
               }

               if (packet.getType() == 51) {
                  this.lastEntityID = packet.getEntityID();
                  if (this.explode.getValue() && this.check()) {
                     EntityEnderCrystal crystal = (EntityEnderCrystal)mc.world.getEntityByID(this.lastEntityID);
                     if (crystal != null
                        && this.PacketExplode.getValue()
                        && this.PacketExplodeTimer.passedMs(this.PacketExplodeDelay.getValue().intValue())
                        && this.canHitCrystal(crystal)) {
                        this.PacketExplode(this.lastEntityID);
                        this.PacketExplodeTimer.reset();
                     }
                  }
               }
            }

            if (event.getPacket() instanceof SPacketSoundEffect) {
               SPacketSoundEffect packetx = (SPacketSoundEffect)event.getPacket();
               if (packetx.getSound().equals(SoundEvents.ENTITY_EXPERIENCE_BOTTLE_THROW) || packetx.getSound().equals(SoundEvents.ENTITY_ITEM_BREAK)) {
                  this.canPredictHit = false;
               }

               if (packetx.getSound().equals(SoundEvents.ENTITY_GENERIC_EXPLODE)) {
                  this.ShouldInfoLastBreak = true;
                  this.crystals++;
               }
            }
         }
      }
   );
   @EventHandler
   private final Listener<PacketEvent.Send> sendListener = new Listener<>(
      event -> {
         if (mc.world != null && mc.player != null && !mc.player.isDead) {
            if (this.packetOptimize.getValue() && event.getPacket() instanceof CPacketUseEntity && packetList.size() > this.limit.getValue()) {
               event.cancel();
               packetList.clear();
            }

            if (event.getPacket() instanceof CPacketUseEntity
               && this.placeAfter.getValue()
               && !this.post.getValue()
               && ((CPacketUseEntity)event.getPacket()).getAction() == Action.ATTACK) {
               Entity attacked = ((CPacketUseEntity)event.getPacket()).getEntityFromWorld(mc.world);
               if (attacked instanceof EntityEnderCrystal) {
                  long passed = this.PlaceTimer.getTime();
                  this.PlaceTimer.setMs(this.placeDelay.getValue() + 1);
                  this.place(false);
                  this.PlaceTimer.setTime(passed);
               }
            }

            if (this.rotate.getValue() && this.lastHitVec != null) {
               Vec2f vec = RotationUtil.getRotationTo(this.lastHitVec);
               if (event.getPacket() instanceof Rotation) {
                  ((Rotation)event.getPacket()).yaw = vec.x;
                  ((Rotation)event.getPacket()).pitch = vec.y;
               }

               if (event.getPacket() instanceof PositionRotation) {
                  ((PositionRotation)event.getPacket()).yaw = vec.x;
                  ((PositionRotation)event.getPacket()).pitch = vec.y;
               }
            }

            if (event.getPacket() instanceof CPacketHeldItemChange) {
               int slot = ((CPacketHeldItemChange)event.getPacket()).getSlotId();
               if (slot != this.lastSlot) {
                  this.lastSlot = slot;
                  this.cooldownTimer.reset();
               }
            }
         }
      }
   );
   @EventHandler
   private final Listener<EntityRemovedEvent> entityRemovedEventListener = new Listener<>(event -> {
      if (event.getEntity().entityId == this.crystalId && this.placeOnRemove.getValue()) {
         long passed = this.PlaceTimer.getTime();
         this.PlaceTimer.setMs(this.placeDelay.getValue() + 1);
         this.place(false);
         this.PlaceTimer.setTime(passed);
      }
   });
   boolean tryCalc;
   int c = 0;

   public void windowClick(int windowId, int slotId, int mouseButton, ClickType type, EntityPlayer player, boolean back) {
      short short1 = player.openContainer.getNextTransactionID(player.inventory);
      ItemStack itemStack = ItemStack.EMPTY;
      if (!this.packetSwitch.getValue()) {
         itemStack = player.openContainer.slotClick(slotId, mouseButton, type, player);
      }

      mc.player
         .connection
         .sendPacket(
            new CPacketClickWindow(
               windowId, slotId, mouseButton, type, back && this.forceUpdate.getValue() ? Items.END_CRYSTAL.getDefaultInstance() : itemStack, short1
            )
         );
      mc.playerController.updateController();
      mc.player.openContainer.detectAndSendChanges();
   }

   private void switchToCrystal(int slot, boolean bypass, boolean shouldSwitch, boolean back, Runnable runnable) {
      int oldslot = mc.player.inventory.currentItem;
      if (shouldSwitch && slot >= 0 && slot != oldslot) {
         if (bypass) {
            if (slot < 9) {
               slot += 36;
            }

            int id = mc.player.inventoryContainer.windowId;
            int finalSlot = slot;
            Locks.acquire(Locks.PLACE_SWITCH_LOCK, () -> {
               Locks.acquire(Locks.WINDOW_CLICK_LOCK, () -> this.windowClick(id, finalSlot, oldslot, ClickType.SWAP, mc.player, false));
               runnable.run();
               mc.playerController.updateController();
               mc.player.openContainer.detectAndSendChanges();
               Locks.acquire(Locks.WINDOW_CLICK_LOCK, () -> this.windowClick(id, finalSlot, oldslot, ClickType.SWAP, mc.player, true));
            });
         } else if (slot < 9) {
            boolean packetSwitch = back && this.packetSwitch.getValue();
            if (packetSwitch) {
               mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
            } else {
               mc.player.inventory.currentItem = slot;
               mc.playerController.updateController();
            }

            runnable.run();
            if (back) {
               if (packetSwitch) {
                  mc.player.connection.sendPacket(new CPacketHeldItemChange(oldslot));
               } else {
                  mc.player.inventory.currentItem = oldslot;
                  mc.playerController.updateController();
               }
            }
         }
      } else {
         runnable.run();
      }
   }

   private void switchTo(int slot, boolean bypass, boolean back, Runnable runnable) {
      int oldslot = mc.player.inventory.currentItem;
      if (slot >= 0 && slot != oldslot) {
         if (bypass) {
            if (slot < 9) {
               slot += 36;
            }

            int id = mc.player.inventoryContainer.windowId;
            int finalSlot = slot;
            Locks.acquire(Locks.PLACE_SWITCH_LOCK, () -> {
               Locks.acquire(Locks.WINDOW_CLICK_LOCK, () -> this.windowClick(id, finalSlot, oldslot, ClickType.SWAP, mc.player, false));
               runnable.run();
               Locks.acquire(Locks.WINDOW_CLICK_LOCK, () -> this.windowClick(id, finalSlot, oldslot, ClickType.SWAP, mc.player, false));
            });
         } else if (slot < 9) {
            boolean packetSwitch = back && this.packetSwitch.getValue();
            if (packetSwitch) {
               mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
            } else {
               mc.player.inventory.currentItem = slot;
               mc.playerController.updateController();
            }

            runnable.run();
            if (back) {
               if (packetSwitch) {
                  mc.player.connection.sendPacket(new CPacketHeldItemChange(oldslot));
               } else {
                  mc.player.inventory.currentItem = oldslot;
                  mc.playerController.updateController();
               }
            }
         }
      } else {
         runnable.run();
      }
   }

   public static double getRange(Vec3d a, double x, double y, double z) {
      double xl = a.x - x;
      double yl = a.y - y;
      double zl = a.z - z;
      return Math.sqrt(xl * xl + yl * yl + zl * zl);
   }

   private boolean check() {
      return this.placeInfo != null && this.placeInfo.target != null && this.placeInfo.target.player != null;
   }

   @Override
   public void onTick() {
      if (this.tryCalc) {
         if (this.UpdateTimer.passedMs(this.updateDelay.getValue().intValue())) {
            if (this.crystalSlot == -1
               && mc.player.getHeldItemOffhand().getItem() != Items.END_CRYSTAL
               && (!this.autoSwitch.getValue() || !this.offhand.getValue())) {
               return;
            }

            this.placeInfo = this.Calc();
            if (!this.check()) {
               this.lastBreakTime = System.currentTimeMillis();
               this.switchOffhand(false);
               this.pausePA(false);
               this.lastHitVec = null;
               this.placeInfo = null;
               this.crystal = null;
               return;
            }

            if (this.placeInfo.blockPos == null || this.placeInfo.dmg == 0.0) {
               this.placeInfo.blockPos = null;
               this.placeInfo.dmg = 0.0;
               this.switchOffhand(false);
               this.pausePA(false);
               this.lastHitVec = null;
               this.crystal = null;
            }

            AutoEz.INSTANCE.addTargetedPlayer(this.placeInfo.target.player.getName());
            this.UpdateTimer.reset();
         }
      }
   }

   @Override
   public void fast() {
      if (mc.world != null && mc.player != null && !mc.player.isDead) {
         if (this.CalcTimer.passedMs(1000L)) {
            this.CalcTimer.reset();
            this.calculated = true;
         }

         this.crystalSlot = this.getItemHotbar();
         if (this.crystalSlot != -1
            || mc.player.getHeldItemOffhand().getItem() == Items.END_CRYSTAL
            || this.autoSwitch.getValue() && this.offhand.getValue()) {
            this.tryCalc = true;
            if (this.base.getValue()) {
               if (this.waited++ >= this.baseDelay.getValue()) {
                  this.canBase = true;
                  this.waited = 0;
               }
            } else {
               this.canBase = false;
            }

            if (this.check()) {
               this.pausePA(this.pause.getValue());
               if ((this.eat.getValue() || !EntityUtil.isEating())
                  && (this.cooldown.getValue() == 0 || this.cooldownTimer.passedMs(this.cooldown.getValue().intValue()))) {
                  if (this.logic.getValue().equals("BreakPlace")) {
                     this.explode();
                     this.place(this.crystalCheck.getValue());
                  } else {
                     this.place(this.crystalCheck.getValue());
                     this.explode();
                  }
               } else {
                  this.lastHitVec = null;
               }
            }
         } else {
            this.lastBreakTime = System.currentTimeMillis();
            this.placeInfo = null;
            this.switchOffhand(false);
            this.pausePA(false);
            this.lastHitVec = null;
            this.tryCalc = false;
         }
      }
   }

   private void place(boolean check) {
      if (this.place.getValue()) {
         if (this.placeInfo != null && this.placeInfo.blockPos != null) {
            boolean detected = true;

            for (Entity entity : mc.world.loadedEntityList) {
               if (entity instanceof EntityEnderCrystal && this.crystalPlaceBoxIntersectsCrystalBox(this.placeInfo.blockPos, entity)) {
                  detected = false;
                  this.crystal = (EntityEnderCrystal)entity;
                  break;
               }
            }

            if (detected) {
               this.crystal = null;
            }

            boolean useOffhand = mc.player.getHeldItemOffhand().getItem() == Items.END_CRYSTAL;
            if (mc.player.inventory.currentItem != this.crystalSlot && !useOffhand) {
               if (!this.autoSwitch.getValue()) {
                  return;
               }

               if (this.offhand.getValue()) {
                  this.switchOffhand(true);
                  return;
               }
            }

            Block block = BlockUtil.getBlock(this.placeInfo.blockPos);
            if (block != Blocks.BEDROCK && block != Blocks.OBSIDIAN && this.base.getValue()) {
               int obby = BurrowUtil.findBlock(BlockObsidian.class, this.findInventory());
               if (obby == -1) {
                  return;
               }

               this.switchTo(
                  obby,
                  this.baseBypass.getValue(),
                  true,
                  () -> BurrowUtil.placeBlock(
                     this.placeInfo.blockPos, EnumHand.MAIN_HAND, this.rotate.getValue(), this.packetPlace.getValue(), false, this.swing.getValue()
                  )
               );
               this.canBase = false;
            }

            if (this.PlaceTimer.passedMs(this.placeDelay.getValue().intValue()) && (detected || !check)) {
               EnumHand hand = useOffhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
               EnumFacing facing = this.placeInfo.blockPos.getY() == 255 ? EnumFacing.DOWN : EnumFacing.UP;
               Vec3d add = new Vec3d(0.5, facing == EnumFacing.UP ? 1 : 0, 0.5);
               Vec3d vec = new Vec3d(this.placeInfo.blockPos.x, this.placeInfo.blockPos.y, this.placeInfo.blockPos.z)
                  .add(add);
               this.lastHitVec = vec;
               this.switchToCrystal(
                  this.crystalSlot,
                  this.findInventory(),
                  !useOffhand && this.autoSwitch.getValue(),
                  this.switchBack.getValue(),
                  () -> {
                     if (this.packet.getValue()) {
                        mc.player
                           .connection
                           .sendPacket(
                              new CPacketPlayerTryUseItemOnBlock(
                                 this.placeInfo.blockPos, facing, hand, (float)add.x, (float)add.y, (float)add.z
                              )
                           );
                     } else {
                        mc.playerController.processRightClickBlock(mc.player, mc.world, this.placeInfo.blockPos, facing, vec, hand);
                     }
                  }
               );
               if (this.swing.getValue()) {
                  if (this.packetSwing.getValue()) {
                     mc.player.connection.sendPacket(new CPacketAnimation(hand));
                  } else {
                     mc.player.swingArm(hand);
                  }
               }

               this.placements++;
               this.PlaceTimer.reset();
            }

            if (this.PredictHit.getValue()
               && DamageUtil.calculateCrystalDamage(
                     this.placeInfo.target.player,
                     this.placeInfo.target.position,
                     this.placeInfo.target.boundingBox,
                     this.placeInfo.blockPos.x + 0.5,
                     this.placeInfo.blockPos.y + 1,
                     this.placeInfo.blockPos.z + 0.5
                  )
                  > this.breakMinDmg.getValue().intValue()) {
               try {
                  if (!this.canPredictHit) {
                     this.PlaceTimer.reset();
                     return;
                  }

                  if (mc.player.getHealth() + mc.player.getAbsorptionAmount() > this.maxSelfDMG.getValue()
                     && this.lastEntityID != -1
                     && this.lastCrystal != null
                     && this.canPredictHit) {
                     for (int i = 0; i < this.PredictHitFactor.getValue(); i++) {
                        this.PacketExplode(this.lastEntityID + i + 2);
                     }
                  }
               } catch (Exception var9) {
               }
            }
         } else {
            this.crystal = null;
         }
      }
   }

   public List<EntityPlayer> getTargets() {
      return PlayerUtil.getNearPlayers(this.enemyRange.getValue().intValue(), this.maxTarget.getValue());
   }

   public AutoCrystal.PlaceInfo Calc() {
      AutoCrystal.PlaceInfo best = new AutoCrystal.PlaceInfo(
         new AutoCrystal.PlayerInfo(PlayerUtil.getNearestPlayer(this.enemyRange.getValue().intValue())), null, 0.0, 0.0
      );
      List<BlockPos> default_blocks;
      if (this.wall.getValue() && this.wallAI.getValue()) {
         double TempRange = this.placeRange.getValue();
         double temp2 = TempRange - this.StuckTimes * 0.5;
         if (this.StuckTimes > 0) {
            TempRange = this.placeRange.getValue();
            if (temp2 > this.placeWallRange.getValue()) {
               TempRange = temp2;
            } else if (this.placeWallRange.getValue() < this.placeRange.getValue()) {
               TempRange = 3.0;
            }
         }

         default_blocks = this.renditions(TempRange);
      } else {
         default_blocks = this.renditions(this.placeRange.getValue());
      }

      this.settings = new PredictUtil.PredictSettings(
         this.tickPredict.getValue(),
         this.calculateYPredict.getValue(),
         this.startDecrease.getValue(),
         this.exponentStartDecrease.getValue(),
         this.decreaseY.getValue(),
         this.exponentDecreaseY.getValue(),
         this.splitXZ.getValue(),
         this.manualOutHole.getValue(),
         this.aboveHoleManual.getValue(),
         this.stairPredict.getValue(),
         this.nStair.getValue(),
         this.speedActivationStair.getValue()
      );
      EntityPlayer player = mc.player;
      if (this.self.getValue()) {
         player = PredictUtil.predictPlayer(player, this.settings);
      }

      AutoCrystal.PlayerInfo self = new AutoCrystal.PlayerInfo(mc.player, player.getPositionVector(), player.getEntityBoundingBox());
      boolean calcBase = true;

      for (EntityPlayer target : this.getTargets()) {
         EntityPlayer origin = target;
         if (this.target.getValue()) {
            target = PredictUtil.predictPlayer(target, this.settings);
         }

         AutoCrystal.PlayerInfo targetPlayer = new AutoCrystal.PlayerInfo(origin, target.getPositionVector(), target.getEntityBoundingBox());
         this.canPredictHit = (!this.PredictHit.getValue() || !targetPlayer.player.getHeldItemMainhand().getItem().equals(Items.EXPERIENCE_BOTTLE))
               && !targetPlayer.player.getHeldItemOffhand().getItem().equals(Items.EXPERIENCE_BOTTLE)
            || !ModuleManager.getModule("AutoMend").isEnabled();

         for (BlockPos blockPos : default_blocks) {
            boolean shouldBase = mc.world.getBlockState(blockPos).getBlock() != Blocks.BEDROCK
               && mc.world.getBlockState(blockPos).getBlock() != Blocks.OBSIDIAN;
            if (!shouldBase
               || this.canBase
                  && calcBase
                  && blockPos.y < (int)(targetPlayer.player.posY + 0.5)
                  && BurrowUtil.findHotbarBlock(BlockObsidian.class) != -1
                  && !(LemonClient.speedUtil.getPlayerSpeed(targetPlayer.player) > this.maxSpeed.getValue())) {
               double dmg = this.MineDetect.getValue()
                  ? DamageUtil.calculateCrystalDamageMine(
                     targetPlayer.player,
                     targetPlayer.position,
                     targetPlayer.boundingBox,
                     blockPos.getX() + 0.5,
                     blockPos.getY() + 1,
                     blockPos.getZ() + 0.5
                  )
                  : DamageUtil.calculateCrystalDamage(
                     targetPlayer.player,
                     targetPlayer.position,
                     targetPlayer.boundingBox,
                     blockPos.getX() + 0.5,
                     blockPos.getY() + 1,
                     blockPos.getZ() + 0.5
                  );
               if (dmg != 0.0 && !(dmg < best.dmg)) {
                  if (shouldBase) {
                     if ((int)dmg == (int)best.dmg || dmg < this.baseMinDamage.getValue().intValue()) {
                        continue;
                     }
                  } else if (dmg >= this.toggleDamage.getValue().intValue()) {
                     calcBase = false;
                  }

                  double selfDmg = 0.0;
                  if (this.godMode.getValue().equals("NoGodMode") || this.godMode.getValue().equals("Auto") && !mc.player.isCreative()) {
                     selfDmg = DamageUtil.calculateCrystalDamage(
                        self.player,
                        self.position,
                        self.boundingBox,
                        blockPos.getX() + 0.5,
                        blockPos.getY() + 1,
                        blockPos.getZ() + 0.5
                     );
                  }

                  if (selfDmg == 0.0
                     || !(selfDmg + this.balance.getValue() >= self.health) && !(selfDmg + this.balance.getValue() > this.maxSelfDMG.getValue())
                     || this.forcePlace.getValue() && !(dmg <= targetPlayer.health)) {
                     double minDamage = this.minDamage.getValue();
                     if (this.canFacePlace(targetPlayer)) {
                        minDamage = this.fpMinDmg.getValue();
                     }

                     if (dmg >= minDamage) {
                        best = new AutoCrystal.PlaceInfo(targetPlayer, blockPos, dmg, selfDmg);
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   public void explode() {
      if (this.explode.getValue()) {
         EntityEnderCrystal crystal = this.crystal == null
            ? mc.world
               .loadedEntityList
               .stream()
               .filter(e -> e instanceof EntityEnderCrystal && this.canHitCrystal((EntityEnderCrystal)e))
               .map(e -> (EntityEnderCrystal)e)
               .min(Comparator.comparing(e -> mc.player.getDistance(e)))
               .orElse(null)
            : this.crystal;
         if (crystal != null) {
            this.lastCrystal = crystal;
            if (this.StuckTimes > 0) {
               this.StuckTimes = 0;
            }

            this.lastHitVec = new Vec3d(crystal.posX, crystal.posY, crystal.posZ);
            this.ExplodeCrystal(this.lastCrystal);
            if (this.lastBreakTime == 0L) {
               this.lastBreakTime = System.currentTimeMillis();
            }

            this.afterAttacking = true;
         } else {
            this.lastBreakTime = System.currentTimeMillis();
            this.afterAttacking = false;
            this.StuckTimes++;
         }
      }
   }

   public void ExplodeCrystal(Entity crystal) {
      if (crystal != null && this.ExplodeTimer.passedMs(this.hitDelay.getValue().intValue()) && mc.getConnection() != null) {
         this.PacketExplode(crystal.getEntityId());
         this.ExplodeTimer.reset();
         if (this.ClientSide.getValue()) {
            for (Entity o : mc.world.getLoadedEntityList()) {
               if (o instanceof EntityEnderCrystal && o.getDistance(o.posX, o.posY, o.posZ) <= 6.0) {
                  o.setDead();
               }
            }

            mc.world.removeAllEntities();
         }

         if (this.multiPlace.getValue() && this.placements >= 3) {
            this.placements = 0;
            this.afterAttacking = true;
         }
      }
   }

   public void PacketExplode(int i) {
      if (this.check() && (this.lastCrystal != null && this.canHitCrystal(this.lastCrystal) || mc.world.getEntityByID(i) == null)) {
         this.crystalId = i;

         try {
            int slot = -1;
            if (this.antiWeakness.getValue()
               && mc.player.isPotionActive(MobEffects.WEAKNESS)
               && (
                  !mc.player.isPotionActive(MobEffects.STRENGTH)
                     || Objects.requireNonNull(mc.player.getActivePotionEffect(MobEffects.STRENGTH)).getAmplifier() < 1
               )) {
               for (int b = 0; b < (this.findInventory() ? 36 : 9); b++) {
                  ItemStack stack = mc.player.inventory.getStackInSlot(b);
                  if (stack != ItemStack.EMPTY) {
                     if (stack.getItem() instanceof ItemSword) {
                        slot = b;
                        break;
                     }

                     if (stack.getItem() instanceof ItemTool) {
                        slot = b;
                     }
                  }
               }
            }

            this.switchTo(slot, this.antiWeakMode.getValue().equals("Bypass"), !this.antiWeakMode.getValue().equals("Normal"), () -> {
               CPacketUseEntity crystal = new CPacketUseEntity();
               setEntityId(crystal, i);
               setAction(crystal, Action.ATTACK);
               mc.player.connection.sendPacket(crystal);
               if (this.packetOptimize.getValue()) {
                  packetList.add(crystal);
               }
            });
            EnumHand hand = mc.player.getHeldItemOffhand().getItem() == Items.END_CRYSTAL ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            if (this.swing.getValue()) {
               if (this.packetSwing.getValue()) {
                  mc.player.connection.sendPacket(new CPacketAnimation(hand));
               } else {
                  mc.player.swingArm(hand);
               }
            }
         } catch (Exception var5) {
         }
      }
   }

   public static void setEntityId(CPacketUseEntity packet, int entityId) {
      ((AccessorCPacketUseEntity)packet).setId(entityId);
   }

   public static void setAction(CPacketUseEntity packet, Action action) {
      ((AccessorCPacketUseEntity)packet).setAction(action);
   }

   public AutoCrystal.CrystalInfo getBestDmg(EntityEnderCrystal crystal) {
      AutoCrystal.CrystalInfo best = new AutoCrystal.CrystalInfo(crystal, null, 0.0);

      for (EntityPlayer entityPlayer : this.getTargets()) {
         EntityPlayer player = this.target.getValue() ? PredictUtil.predictPlayer(entityPlayer, this.settings) : entityPlayer;
         AutoCrystal.PlayerInfo target = new AutoCrystal.PlayerInfo(entityPlayer, player.getPositionVector(), player.getEntityBoundingBox());
         double dmg = DamageUtil.calculateCrystalDamage(
            target.player, target.position, target.boundingBox, crystal.posX, crystal.posY, crystal.posZ
         );
         if (dmg != 0.0) {
            AutoCrystal.CrystalInfo get = new AutoCrystal.CrystalInfo(crystal, target, dmg);
            if (dmg >= target.health) {
               return get;
            }

            if (dmg > best.damage) {
               best = get;
            }
         }
      }

      return best;
   }

   public List<BlockPos> renditions(double range) {
      NonNullList<BlockPos> positions = NonNullList.create();
      positions.addAll(
         EntityUtil.getSphere(PlayerUtil.getEyesPos(), range, range, false, true, 0).stream().filter(this::canPlaceCrystal).collect(Collectors.toList())
      );
      return positions;
   }

   public boolean canPlaceCrystal(BlockPos blockPos) {
      if (PlayerUtil.getDistanceI(blockPos) > this.placeRange.getValue()) {
         return false;
      } else if (this.wall.getValue() && PlayerUtil.getDistanceI(blockPos) > this.placeWallRange.getValue() && !CrystalUtil.calculateRaytrace(blockPos)) {
         return false;
      } else {
         BlockPos boost = blockPos.add(0, 1, 0);
         BlockPos boost2 = blockPos.add(0, 2, 0);
         if (!BlockUtil.isAirBlock(boost)) {
            return false;
         } else if (!this.highVersion.getValue() && !BlockUtil.isAirBlock(boost2)) {
            return false;
         } else {
            if (mc.world.getBlockState(blockPos).getBlock() != Blocks.BEDROCK
               && mc.world.getBlockState(blockPos).getBlock() != Blocks.OBSIDIAN) {
               if (!this.canBase || this.base.getValue()) {
                  return false;
               }

               if (!BlockUtil.isAirBlock(blockPos) || this.intersectsWithEntity(blockPos)) {
                  return false;
               }

               if (BurrowUtil.getFirstFacing(blockPos) == null) {
                  return false;
               }
            }

            boolean recall = false;

            for (Entity entity : mc.world.loadedEntityList) {
               if (entity instanceof EntityEnderCrystal && this.crystalPlaceBoxIntersectsCrystalBox(blockPos, entity)) {
                  recall = true;
                  break;
               }
            }

            for (Entity entityx : mc.world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(boost))) {
               if (!(entityx instanceof EntityEnderCrystal)
                  && (!recall || !(entityx instanceof EntityItem) && !(entityx instanceof EntityXPOrb) && !(entityx instanceof EntityExpBottle))) {
                  return false;
               }
            }

            if (!this.highVersion.getValue()) {
               for (Entity entityxx : mc.world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(boost2))) {
                  if (!(entityxx instanceof EntityEnderCrystal)
                     && (!recall || !(entityxx instanceof EntityItem) && !(entityxx instanceof EntityXPOrb) && !(entityxx instanceof EntityExpBottle))) {
                     return false;
                  }
               }
            }

            if (this.afterAttacking && this.lastCrystal != null) {
               for (Entity entityxxx : mc.world.loadedEntityList) {
                  if (entityxxx instanceof EntityEnderCrystal) {
                     EntityEnderCrystal enderCrystal = (EntityEnderCrystal)entityxxx;
                     if (!(Math.abs(enderCrystal.posY - (blockPos.getY() + 1)) >= 2.0)) {
                        double d2 = this.lastCrystal.getDistance(blockPos.getX() + 0.5, blockPos.getY() + 1, blockPos.getZ() + 0.5);
                        if (!(d2 <= 6.0)
                           && !(
                              getRange(
                                    enderCrystal.getPositionVector(), blockPos.getX() + 0.5, blockPos.getY() + 1, blockPos.getZ() + 0.5
                                 )
                                 >= 2.0
                           )) {
                           return false;
                        }
                     }
                  }
               }
            }

            return true;
         }
      }
   }

   public boolean canHitCrystal(EntityEnderCrystal crystal) {
      if (crystal == null) {
         return false;
      } else if (mc.player.getDistance(crystal) > this.breakRange.getValue()) {
         return false;
      } else if (this.wall.getValue() && mc.player.getDistance(crystal) > this.breakWallRange.getValue() && !CrystalUtil.calculateRaytrace(crystal)) {
         return false;
      } else if (crystal == this.crystal
         && DamageUtil.calculateCrystalDamage(
               this.placeInfo.target.player,
               this.placeInfo.target.position,
               this.placeInfo.target.boundingBox,
               this.placeInfo.blockPos.x + 0.5,
               this.placeInfo.blockPos.y + 1,
               this.placeInfo.blockPos.z + 0.5
            )
            >= this.breakMinDmg.getValue().intValue()) {
         return true;
      } else {
         float healthSelf = mc.player.getHealth() + mc.player.getAbsorptionAmount();
         float selfDamage = 0.0F;
         if (this.godMode.getValue().equals("NoGodMode") || this.godMode.getValue().equals("Auto") && !mc.player.isCreative()) {
            EntityPlayer player = (EntityPlayer)(this.self.getValue() ? PredictUtil.predictPlayer(mc.player, this.settings) : mc.player);
            AutoCrystal.PlayerInfo self = new AutoCrystal.PlayerInfo(mc.player, player.getPositionVector(), player.getEntityBoundingBox());
            selfDamage = DamageUtil.calculateCrystalDamage(
               self.player, self.position, self.boundingBox, crystal.posX, crystal.posY, crystal.posZ
            );
         }

         AutoCrystal.CrystalInfo bestTarget = this.getBestDmg(crystal);
         if (bestTarget.player == null) {
            return false;
         } else if (selfDamage == 0.0F
            || !(selfDamage + this.balance.getValue() >= healthSelf) && !(selfDamage + this.balance.getValue() > this.maxSelfDMG.getValue())) {
            double minDamage = this.breakMinDmg.getValue().intValue();
            if (this.canFacePlace(bestTarget.player)) {
               minDamage = this.fpMinDmg.getValue();
            }

            return bestTarget.damage >= minDamage;
         } else {
            return !this.forceBreak.getValue() ? false : bestTarget.player.health <= bestTarget.damage;
         }
      }
   }

   public boolean canFacePlace(AutoCrystal.PlayerInfo target) {
      if (target != null && target.player != null && this.facePlace.getValue()) {
         if (target.health < this.BlastHealth.getValue().intValue()) {
            return true;
         } else {
            for (ItemStack itemStack : target.player.getArmorInventoryList()) {
               if (!itemStack.isEmpty() && itemStack.getCount() <= this.armorCount.getValue()) {
                  float dmg = ((float)itemStack.getMaxDamage() - itemStack.getItemDamage()) / itemStack.getMaxDamage();
                  if (dmg < this.armorRate.getValue().intValue() / 100.0F) {
                     return true;
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private int getItemHotbar() {
      for (int i = 0; i < (this.findInventory() ? 36 : 9); i++) {
         Item item = mc.player.inventory.getStackInSlot(i).getItem();
         if (Item.getIdFromItem(item) == Item.getIdFromItem(Items.END_CRYSTAL)) {
            return i;
         }
      }

      return -1;
   }

   private boolean findInventory() {
      return this.bypass.getValue() && this.switchBack.getValue();
   }

   private void switchOffhand(boolean value) {
      if (ModuleManager.isModuleEnabled(OffHand.class)) {
         OffHand.INSTANCE.autoCrystal = value;
      }
   }

   private void pausePA(boolean value) {
      if (ModuleManager.isModuleEnabled(PistonAura.class)) {
         PistonAura.INSTANCE.autoCrystal = value;
      }

      if (ModuleManager.isModuleEnabled(PullCrystal.class)) {
         PullCrystal.INSTANCE.autoCrystal = value;
      }
   }

   private boolean intersectsWithEntity(BlockPos pos) {
      for (Entity entity : mc.world.loadedEntityList) {
         if (!(entity instanceof EntityItem)
            && !(entity instanceof EntityXPOrb)
            && !(entity instanceof EntityExpBottle)
            && new AxisAlignedBB(pos).intersects(entity.getEntityBoundingBox())) {
            return true;
         }
      }

      return false;
   }

   private boolean crystalPlaceBoxIntersectsCrystalBox(BlockPos placePos, Entity entity) {
      return entity.boundingBox
         .intersects(
            new AxisAlignedBB(
               placePos.x - 0.5,
               placePos.y,
               placePos.z - 0.5,
               placePos.x + 1.5,
               placePos.y + (this.highVersion.getValue() ? 1 : 2),
               placePos.z + 1.5
            )
         );
   }

   @Override
   public void onEnable() {
      this.lastBreakTime = System.currentTimeMillis();
      this.lastEntityID = -1;
      this.crystals = this.c = 0;
      this.updateTime = System.currentTimeMillis();
      this.startTime = System.currentTimeMillis();
      this.ShouldInfoLastBreak = false;
      this.afterAttacking = false;
      this.canPredictHit = true;
      this.PlaceTimer.reset();
      this.ExplodeTimer.reset();
      this.PacketExplodeTimer.reset();
      this.UpdateTimer.reset();
      this.CalcTimer.reset();
      packetList.clear();
      this.lastSlot = mc.player.inventory.currentItem;
      this.lastHitVec = null;
      this.placeInfo = null;
      this.movingPlaceNow = new Vec3d(-1.0, -1.0, -1.0);
   }

   @Override
   public void onDisable() {
      this.switchOffhand(false);
      this.pausePA(false);
      this.lastHitVec = null;
      this.StuckTimes = 0;
      packetList.clear();
   }

   @Override
   public String getHudInfo() {
      if (!this.check()) {
         return "";
      } else {
         if (this.ShouldInfoLastBreak) {
            this.infoBreakTime = System.currentTimeMillis() - this.lastBreakTime;
            this.lastBreakTime = 0L;
            this.ShouldInfoLastBreak = false;
         }

         if (this.calculated) {
            this.c = this.crystals;
            this.calculated = false;
            this.crystals = 0;
         }

         return "["
            + ChatFormatting.WHITE
            + this.placeInfo.target.player.getName()
            + (this.showBreakDelay.getValue() ? ", " + this.infoBreakTime + "ms" : "")
            + (this.speedDebug.getValue() ? ", " + this.c + "c/s" : "")
            + ChatFormatting.GRAY
            + "]";
      }
   }

   @Override
   public void onWorldRender(RenderEvent event) {
      if (mc.world != null && mc.player != null) {
         BlockPos placing = this.placeInfo == null ? null : this.placeInfo.blockPos;
         if (placing != this.lastBestPlace) {
            if (placing != null && this.lastBestPlace == null) {
               this.movingPlaceNow = new Vec3d(
                  this.placeInfo.blockPos.getX(), this.placeInfo.blockPos.getY(), this.placeInfo.blockPos.getZ()
               );
            }

            this.updateTime = System.currentTimeMillis();
            if (placing == null) {
               this.startTime = System.currentTimeMillis();
            } else if (this.lastBestPlace == null) {
               this.startTime = System.currentTimeMillis();
            }

            this.lastBestPlace = placing;
         }

         if (this.lastBestPlace != null) {
            if (this.movingPlaceNow.x == -1.0 && this.movingPlaceNow.y == -1.0 && this.movingPlaceNow.z == -1.0) {
               this.movingPlaceNow = new Vec3d(this.lastBestPlace.getX(), this.lastBestPlace.getY(), this.lastBestPlace.getZ());
            }

            if (this.movingTime.getValue() == 0) {
               this.movingPlaceNow = new Vec3d(this.lastBestPlace);
            } else {
               this.movingPlaceNow = new Vec3d(
                  this.movingPlaceNow.x
                     + (this.lastBestPlace.getX() - this.movingPlaceNow.x)
                        * this.toDelta(this.updateTime, this.movingTime.getValue().intValue()),
                  this.movingPlaceNow.y
                     + (this.lastBestPlace.getY() - this.movingPlaceNow.y)
                        * this.toDelta(this.updateTime, this.movingTime.getValue().intValue()),
                  this.movingPlaceNow.z
                     + (this.lastBestPlace.getZ() - this.movingPlaceNow.z)
                        * this.toDelta(this.updateTime, this.movingTime.getValue().intValue())
               );
            }
         }

         if (this.movingPlaceNow.x != -1.0 || this.movingPlaceNow.y != -1.0 || this.movingPlaceNow.z != -1.0) {
            this.drawBoxMain(this.movingPlaceNow.x, this.movingPlaceNow.y, this.movingPlaceNow.z);
         }
      }
   }

   AxisAlignedBB getBox(double x, double y, double z) {
      double maxX = x + 1.0;
      double maxZ = z + 1.0;
      return new AxisAlignedBB(x, y, z, maxX, y + 1.0, maxZ);
   }

   float toDelta(long start, float length) {
      float value = (float)this.toDelta(start) / length;
      if (value > 1.0F) {
         value = 1.0F;
      }

      if (value < 0.0F) {
         value = 0.0F;
      }

      return value;
   }

   long toDelta(long start) {
      return System.currentTimeMillis() - start;
   }

      void drawBoxMain(double x, double y, double z) {
      AxisAlignedBB box = this.getBox(x, y, z);
      float size;
      if (this.check() && this.placeInfo.blockPos != null) {
         size = this.toDelta(this.startTime, this.lifeTime.getValue().intValue());
      } else {
         size = 1.0F - this.toDelta(this.startTime, this.lifeTime.getValue().intValue());
      }

      if (this.scale.getValue()) {
         box = box.grow((1.0F - size) * (1.0F - size) / 2.0F - 1.0F);
      }

      if (this.flat.getValue()) {
         box = new AxisAlignedBB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
      }

      int alpha = (int)(this.alpha.getValue().intValue() * size);
      int outAlpha = (int)(this.outAlpha.getValue().intValue() * size);
      
      // Biến damageText đầu tiên (kiểu String)
      String damageText = this.mode.getValue();
      switch (damageText) {
         case "Outline":
            RenderUtil.drawBoundingBox(box, this.width.getValue().intValue(), new GSColor(this.color.getValue(), outAlpha));
            break;
         case "Solid":
            RenderUtil.drawBox(box, true, this.flat.getValue() ? 0.0 : 1.0, new GSColor(this.color.getValue(), alpha), 63);
            break;
         case "Both":
            RenderUtil.drawBox(box, true, this.flat.getValue() ? 0.0 : 1.0, new GSColor(this.color.getValue(), alpha), 63);
            RenderUtil.drawBoundingBox(box, this.width.getValue().intValue(), new GSColor(this.color.getValue(), outAlpha));
      }

      if (this.showDamage.getValue() && this.check() && this.placeInfo.blockPos != null) {
         box = this.getBox(x, y, z);
         
         // Đã đổi tên thành damageDisplay (kiểu String[]) để tránh lỗi 'already defined'
         String[] damageDisplay = new String[]{String.format("%.1f", this.placeInfo.dmg)};
         if (this.showSelfDamage.getValue()) {
            damageDisplay = new String[]{String.format("%.1f", this.placeInfo.dmg) + "/" + String.format("%.1f", this.placeInfo.selfDmg)};
         }

         RenderUtil.drawNametag(
            box.minX + 0.5, box.minY + 0.5, box.minZ + 0.5, damageDisplay, new GSColor(255, 255, 255), 1, 0.02666666666666667, 0.0
         );
      }
   }

   public static class CrystalInfo {
      EntityEnderCrystal crystal;
      AutoCrystal.PlayerInfo player;
      double damage;

      public CrystalInfo(EntityEnderCrystal crystal, AutoCrystal.PlayerInfo player, double damage) {
         this.crystal = crystal;
         this.player = player;
         this.damage = damage;
      }
   }

   public static class PlaceInfo {
      public BlockPos blockPos;
      public AutoCrystal.PlayerInfo target;
      public double dmg;
      public double selfDmg;

      public PlaceInfo(AutoCrystal.PlayerInfo target, BlockPos block, double dmg, double selfDmg) {
         this.blockPos = block;
         this.target = target;
         this.dmg = dmg;
         this.selfDmg = selfDmg;
      }
   }

   public static class PlayerInfo {
      EntityPlayer player;
      Vec3d position;
      AxisAlignedBB boundingBox;
      double health;

      public PlayerInfo(EntityPlayer player) {
         this.player = player;
         if (player != null) {
            this.position = player.getPositionVector();
            this.boundingBox = player.getEntityBoundingBox();
            this.health = player.getHealth() + player.getAbsorptionAmount();
         }
      }

      public PlayerInfo(EntityPlayer player, Vec3d position, AxisAlignedBB boundingBox) {
         this.player = player;
         this.position = position;
         this.boundingBox = boundingBox;
         this.health = player.getHealth() + player.getAbsorptionAmount();
      }
   }
}
