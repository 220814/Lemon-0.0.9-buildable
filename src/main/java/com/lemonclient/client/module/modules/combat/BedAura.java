package com.lemonclient.client.module.modules.combat;

import com.lemonclient.api.event.Phase;
import com.lemonclient.api.event.events.OnUpdateWalkingPlayerEvent;
import com.lemonclient.api.event.events.PacketEvent;
import com.lemonclient.api.event.events.RenderEvent;
import com.lemonclient.api.setting.values.BooleanSetting;
import com.lemonclient.api.setting.values.ColorSetting;
import com.lemonclient.api.setting.values.DoubleSetting;
import com.lemonclient.api.setting.values.IntegerSetting;
import com.lemonclient.api.setting.values.ModeSetting;
import com.lemonclient.api.util.misc.Timing;
import com.lemonclient.api.util.player.BurrowUtil;
import com.lemonclient.api.util.player.InventoryUtil;
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
import com.lemonclient.client.module.modules.gui.ColorMain;
import com.lemonclient.client.module.modules.qwq.AutoEz;
import com.lemonclient.mixin.mixins.accessor.AccessorCPacketVehicleMove;
import com.mojang.realmsclient.gui.ChatFormatting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockObsidian;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBed;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketVehicleMove;
import net.minecraft.network.play.client.CPacketEntityAction.Action;
import net.minecraft.network.play.client.CPacketPlayer.PositionRotation;
import net.minecraft.network.play.client.CPacketPlayer.Rotation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

@Module.Declaration(name = "BedAura", category = Category.Combat, priority = 999)
public class BedAura extends Module {
   ModeSetting page = this.registerMode("Page", Arrays.asList("Target", "General", "Delay", "Base", "Calc", "SlowFacePlace", "Switch", "Render"), "General");
   BooleanSetting predict = this.registerBoolean("Predict", true, () -> this.page.getValue().equals("Target"));
   BooleanSetting selfPredict = this.registerBoolean("Predict Self", true, () -> this.page.getValue().equals("Target"));
   DoubleSetting resetRotate = this.registerDouble("Reset Yaw Difference", 15.0, 0.0, 180.0, () -> this.page.getValue().equals("Target"));
   BooleanSetting detect = this.registerBoolean("Detect Ping", false, () -> this.page.getValue().equals("Target"));
   IntegerSetting startTick = this.registerInteger("Start Tick", 2, 0, 30, () -> this.page.getValue().equals("Target"));
   IntegerSetting addTick = this.registerInteger("Add Tick", 4, 0, 10, () -> this.page.getValue().equals("Target"));
   IntegerSetting tickPredict = this.registerInteger("Max Predict Ticks", 10, 0, 30, () -> this.page.getValue().equals("Target"));
   BooleanSetting calculateYPredict = this.registerBoolean("Calculate Y Predict", true, () -> this.page.getValue().equals("Target"));
   IntegerSetting startDecrease = this.registerInteger(
      "Start Decrease", 39, 0, 200, () -> this.calculateYPredict.getValue() && this.page.getValue().equals("Target")
   );
   IntegerSetting exponentStartDecrease = this.registerInteger(
      "Exponent Start", 2, 1, 5, () -> this.calculateYPredict.getValue() && this.page.getValue().equals("Target")
   );
   IntegerSetting decreaseY = this.registerInteger("Decrease Y", 2, 1, 5, () -> this.calculateYPredict.getValue() && this.page.getValue().equals("Target"));
   IntegerSetting exponentDecreaseY = this.registerInteger(
      "Exponent Decrease Y", 1, 1, 3, () -> this.calculateYPredict.getValue() && this.page.getValue().equals("Target")
   );
   BooleanSetting splitXZ = this.registerBoolean("Split XZ", true, () -> this.page.getValue().equals("Target"));
   BooleanSetting manualOutHole = this.registerBoolean("Manual Out Hole", false, () -> this.page.getValue().equals("Target"));
   BooleanSetting aboveHoleManual = this.registerBoolean(
      "Above Hole Manual", false, () -> this.manualOutHole.getValue() && this.page.getValue().equals("Target")
   );
   BooleanSetting stairPredict = this.registerBoolean("Stair Predict", false, () -> this.page.getValue().equals("Target"));
   IntegerSetting nStair = this.registerInteger("N Stair", 2, 1, 4, () -> this.stairPredict.getValue() && this.page.getValue().equals("Target"));
   DoubleSetting speedActivationStair = this.registerDouble(
      "Speed Activation Stair", 0.3, 0.0, 1.0, () -> this.stairPredict.getValue() && this.page.getValue().equals("Target")
   );
   ModeSetting targetMode = this.registerMode(
      "Target", Arrays.asList("Nearest", "Damage", "Health", "Smart"), "Nearest", () -> this.page.getValue().equals("General")
   );
   DoubleSetting smartHealth = this.registerDouble(
      "Smart Health", 16.0, 0.0, 36.0, () -> this.page.getValue().equals("General") && this.targetMode.getValue().equals("Smart")
   );
   BooleanSetting monster = this.registerBoolean("Monsters", true, () -> this.page.getValue().equals("General"));
   BooleanSetting neutral = this.registerBoolean("Neutrals", true, () -> this.page.getValue().equals("General"));
   BooleanSetting animal = this.registerBoolean("Animals", true, () -> this.page.getValue().equals("General"));
   ModeSetting mode = this.registerMode(
      "Mode", Arrays.asList("PlaceBreak", "BreakPlace", "Switch", "Stuck", "Test"), "PlaceBreak", () -> this.page.getValue().equals("General")
   );
   BooleanSetting packetPlace = this.registerBoolean("Packet Place", true, () -> this.page.getValue().equals("General"));
   BooleanSetting placeSwing = this.registerBoolean("Place Swing", true, () -> this.page.getValue().equals("General"));
   BooleanSetting breakSwing = this.registerBoolean("Break Swing", true, () -> this.page.getValue().equals("General"));
   BooleanSetting packetSwing = this.registerBoolean("Packet Swing", true, () -> this.page.getValue().equals("General"));
   BooleanSetting checkBed = this.registerBoolean("Placed Check", false, () -> this.page.getValue().equals("General"));
   BooleanSetting highVersion = this.registerBoolean("1.13", true, () -> this.page.getValue().equals("Base"));
   BooleanSetting placeInAir = this.registerBoolean("Place In Air", true, () -> this.page.getValue().equals("Base"));
   BooleanSetting base = this.registerBoolean("Place Base", true, () -> this.page.getValue().equals("Base") && !this.highVersion.getValue());
   BooleanSetting allPossible = this.registerBoolean(
      "Calc All Possible", true, () -> this.page.getValue().equals("Base") && this.base.getValue() && !this.highVersion.getValue()
   );
   BooleanSetting detectBreak = this.registerBoolean(
      "Detect Break", true, () -> this.page.getValue().equals("Base") && this.base.getValue() && !this.highVersion.getValue()
   );
   BooleanSetting packetBase = this.registerBoolean(
      "Packet Base Place", true, () -> this.page.getValue().equals("Base") && this.base.getValue() && !this.highVersion.getValue()
   );
   BooleanSetting baseSwing = this.registerBoolean(
      "Base Swing", true, () -> this.page.getValue().equals("Base") && this.base.getValue() && !this.highVersion.getValue()
   );
   DoubleSetting toggleDmg = this.registerDouble(
      "Toggle Damage", 8.0, 0.0, 36.0, () -> this.page.getValue().equals("Base") && this.base.getValue() && !this.highVersion.getValue()
   );
   IntegerSetting baseDelay = this.registerInteger(
      "Base Delay", 0, 0, 1000, () -> this.page.getValue().equals("Base") && this.base.getValue() && !this.highVersion.getValue()
   );
   DoubleSetting baseMinDmg = this.registerDouble(
      "Base MinDmg", 8.0, 0.0, 36.0, () -> this.page.getValue().equals("Base") && this.base.getValue() && !this.highVersion.getValue()
   );
   DoubleSetting maxY = this.registerDouble(
      "Max Y", 1.0, 0.0, 3.0, () -> this.page.getValue().equals("Base") && this.base.getValue() && !this.highVersion.getValue()
   );
   DoubleSetting maxSpeed = this.registerDouble(
      "Max Target Speed", 10.0, 0.0, 50.0, () -> this.page.getValue().equals("Base") && this.base.getValue() && !this.highVersion.getValue()
   );
   IntegerSetting calcDelay = this.registerInteger("Calc Delay", 0, 0, 1000, () -> this.page.getValue().equals("Delay"));
   IntegerSetting updateDelay = this.registerInteger("Update Delay", 0, 0, 1000, () -> this.page.getValue().equals("Delay"));
   IntegerSetting placeDelay = this.registerInteger("Place Delay", 50, 0, 1000, () -> this.page.getValue().equals("Delay"));
   IntegerSetting breakDelay = this.registerInteger("Break Delay", 50, 0, 1000, () -> this.page.getValue().equals("Delay"));
   IntegerSetting switchPlaceDelay = this.registerInteger(
      "Switch Place Delay",
      50,
      0,
      1000,
      () -> this.page.getValue().equals("Delay") && (this.mode.getValue().equals("Switch") || this.mode.getValue().equals("Test"))
   );
   IntegerSetting switchBreakDelay = this.registerInteger(
      "Switch Break Delay",
      50,
      0,
      1000,
      () -> this.page.getValue().equals("Delay") && (this.mode.getValue().equals("Switch") || this.mode.getValue().equals("Test"))
   );
   IntegerSetting stuckPlaceDelay = this.registerInteger(
      "Stuck Place Delay",
      50,
      0,
      1000,
      () -> this.page.getValue().equals("Delay") && (this.mode.getValue().equals("Stuck") || this.mode.getValue().equals("Test"))
   );
   IntegerSetting stuckBreakDelay = this.registerInteger(
      "Stuck Break Delay",
      50,
      0,
      1000,
      () -> this.page.getValue().equals("Delay") && (this.mode.getValue().equals("Stuck") || this.mode.getValue().equals("Test"))
   );
   DoubleSetting range = this.registerDouble("Place Range", 5.0, 0.0, 10.0, () -> this.page.getValue().equals("Calc"));
   DoubleSetting yRange = this.registerDouble("Y Range", 2.5, 0.0, 10.0, () -> this.page.getValue().equals("Calc"));
   IntegerSetting enemyRange = this.registerInteger("Enemy Range", 10, 0, 16, () -> this.page.getValue().equals("Calc"));
   IntegerSetting maxEnemies = this.registerInteger("Max Calc Enemies", 5, 0, 25, () -> this.page.getValue().equals("Calc"));
   BooleanSetting autorotate = this.registerBoolean("Auto Rotate", true, () -> this.page.getValue().equals("Calc"));
   BooleanSetting pause = this.registerBoolean("Pause While Burrow", false, () -> this.page.getValue().equals("Calc") && this.autorotate.getValue());
   BooleanSetting pitch = this.registerBoolean("Pitch Down", true, () -> this.page.getValue().equals("Calc") && this.autorotate.getValue());
   DoubleSetting minDmg = this.registerDouble("Min Damage", 8.0, 0.0, 36.0, () -> this.page.getValue().equals("Calc"));
   BooleanSetting ignore = this.registerBoolean("Ignore Self Dmg", false, () -> this.page.getValue().equals("Calc"));
   DoubleSetting maxSelfDmg = this.registerDouble("Max Self Dmg", 10.0, 1.0, 36.0, () -> this.page.getValue().equals("Calc") && !this.ignore.getValue());
   BooleanSetting suicide = this.registerBoolean("Anti Suicide", true, () -> this.page.getValue().equals("Calc"));
   DoubleSetting balance = this.registerDouble("Health Balance", 2.5, 0.0, 10.0, () -> this.page.getValue().equals("Calc"));
   IntegerSetting facePlaceValue = this.registerInteger("FacePlace HP", 8, 0, 36, () -> this.page.getValue().equals("Calc"));
   IntegerSetting armorCount = this.registerInteger("ArmorCount", 1, 0, 64, () -> this.page.getValue().equals("Calc"));
   IntegerSetting armorRate = this.registerInteger("ArmorDamage", 15, 0, 100, () -> this.page.getValue().equals("Calc") && this.armorCount.getValue() > 0);
   DoubleSetting fpMinDmg = this.registerDouble("FP Min Damage", 1.0, 0.0, 36.0, () -> this.page.getValue().equals("Calc"));
   BooleanSetting forcePlace = this.registerBoolean("Force Place", false, () -> this.page.getValue().equals("Calc"));
   ModeSetting handMode = this.registerMode("Hand", Arrays.asList("Main", "Off", "Auto"), "Auto", () -> this.page.getValue().equals("Switch"));
   BooleanSetting autoSwitch = this.registerBoolean(
      "Auto Switch", true, () -> this.page.getValue().equals("Switch") && !this.handMode.getValue().equals("OFff")
   );
   BooleanSetting silentSwitch = this.registerBoolean(
      "Switch Back", true, () -> this.page.getValue().equals("Switch") && this.autoSwitch.getValue() && this.autoSwitch.isVisible()
   );
   BooleanSetting packetSwitch = this.registerBoolean("Packet Switch", true, () -> this.page.getValue().equals("Switch") && this.autoSwitch.isVisible());
   BooleanSetting refill = this.registerBoolean("Refill Beds", true, () -> this.page.getValue().equals("Switch") && this.autoSwitch.isVisible());
   ModeSetting clickMode = this.registerMode(
      "Click Mode",
      Arrays.asList("Quick", "Swap", "Pickup"),
      "Quick",
      () -> this.page.getValue().equals("Switch") && this.refill.getValue() && this.autoSwitch.isVisible()
   );
   ModeSetting refillMode = this.registerMode(
      "Refill Mode", Arrays.asList("All", "Only"), "All", () -> this.page.getValue().equals("Switch") && this.refill.getValue() && this.autoSwitch.isVisible()
   );
   IntegerSetting slotS = this.registerInteger(
      "Slot", 1, 1, 9, () -> this.page.getValue().equals("Switch") && this.refill.getValue() && this.autoSwitch.isVisible()
   );
   BooleanSetting force = this.registerBoolean(
      "Force Refill", false, () -> this.page.getValue().equals("Switch") && this.refill.getValue() && this.autoSwitch.isVisible()
   );
   BooleanSetting slowFP = this.registerBoolean("Slow Face Place", true, () -> this.page.getValue().equals("SlowFacePlace"));
   IntegerSetting slowPlaceDelay = this.registerInteger(
      "SlowFP Place Delay", 500, 0, 1000, () -> this.slowFP.getValue() && this.page.getValue().equals("SlowFacePlace")
   );
   IntegerSetting slowBreakDelay = this.registerInteger(
      "SlowFP Break Delay", 500, 0, 1000, () -> this.slowFP.getValue() && this.page.getValue().equals("SlowFacePlace")
   );
   DoubleSetting slowMinDmg = this.registerDouble(
      "SlowFP Min Dmg", 0.05, 0.0, 36.0, () -> this.slowFP.getValue() && this.page.getValue().equals("SlowFacePlace")
   );
   BooleanSetting showDamage = this.registerBoolean("Render Dmg", true, () -> this.page.getValue().equals("Render"));
   BooleanSetting showSelfDamage = this.registerBoolean("Self Dmg", true, () -> this.page.getValue().equals("Render") && this.showDamage.getValue());
   ColorSetting color = this.registerColor("Hand Color", new GSColor(255, 0, 0, 50), () -> this.page.getValue().equals("Render"));
   ColorSetting color2 = this.registerColor("Base Color", new GSColor(0, 255, 0, 50), () -> this.page.getValue().equals("Render"));
   IntegerSetting alpha = this.registerInteger("Alpha", 60, 0, 255, () -> this.page.getValue().equals("Render"));
   IntegerSetting outAlpha = this.registerInteger("Outline Alpha", 120, 0, 255, () -> this.page.getValue().equals("Render"));
   BooleanSetting gradient = this.registerBoolean("Gradient", true, () -> this.page.getValue().equals("Render"));
   BooleanSetting outGradient = this.registerBoolean("Outline Gradient", true, () -> this.page.getValue().equals("Render"));
   IntegerSetting width = this.registerInteger("Width", 1, 1, 10, () -> this.page.getValue().equals("Render"));
   IntegerSetting movingTime = this.registerInteger("MovingTime", 0, 0, 500, () -> this.page.getValue().equals("Render"));
   IntegerSetting lifeTime = this.registerInteger("FadeTime", 100, 0, 500, () -> this.page.getValue().equals("Render"));
   BooleanSetting renderTest = this.registerBoolean("Render Test", false, () -> this.page.getValue().equals("Render"));
   ModeSetting hudDisplay = this.registerMode("HUD", Arrays.asList("Target", "Damage", "Both", "None"), "None", () -> this.page.getValue().equals("Render"));
   BooleanSetting hudSelfDamage = this.registerBoolean(
      "Show Self Damage",
      false,
      () -> this.page.getValue().equals("Render") && (this.hudDisplay.getValue().equals("Damage") || this.hudDisplay.getValue().equals("Both"))
   );
   HashMap<EntityPlayer, BedAura.MoveRotation> playerSpeed = new HashMap<>();
   BedAura.EntityInfo target = null;
   BlockPos headPos;
   BlockPos basePos;
   BlockPos continuE;
   boolean canBasePlace;
   boolean burrow;
   float damage;
   float selfDamage;
   String face;
   Vec3d movingBaseNow = new Vec3d(-1.0, -1.0, -1.0);
   Vec3d movingHeadNow = new Vec3d(-1.0, -1.0, -1.0);
   BlockPos lastBestBase = null;
   BlockPos lastBestHead = null;
   Timing basetiming = new Timing();
   Timing calctiming = new Timing();
   Timing placetiming = new Timing();
   Timing breaktiming = new Timing();
   Timing updatetiming = new Timing();
   EnumHand hand;
   int slot;
   int maxPredict;
   long updateTimeBase;
   long updateTimeHead;
   long startTime;
   Vec2f rotation;
   BlockPos[] sides = new BlockPos[]{new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0), new BlockPos(0, 0, -1), new BlockPos(0, 0, 1)};
   @EventHandler
   private final Listener<PacketEvent.Send> sendListener = new Listener<>(event -> {
      if (this.rotation != null) {
         if (event.getPacket() instanceof Rotation) {
            ((Rotation)event.getPacket()).yaw = this.rotation.x;
            if (this.pitch.getValue()) {
               ((Rotation)event.getPacket()).pitch = 90.0F;
            }
         }

         if (event.getPacket() instanceof PositionRotation) {
            ((PositionRotation)event.getPacket()).yaw = this.rotation.x;
            if (this.pitch.getValue()) {
               ((PositionRotation)event.getPacket()).pitch = 90.0F;
            }
         }

         if (event.getPacket() instanceof CPacketVehicleMove) {
            ((AccessorCPacketVehicleMove)event.getPacket()).setYaw(this.rotation.x);
         }
      }
   });
   @EventHandler
   private final Listener<OnUpdateWalkingPlayerEvent> onUpdateWalkingPlayerEventListener = new Listener<>(
      event -> {
         if (this.rotation != null && event.getPhase() == Phase.PRE) {
            PlayerPacket packet = new PlayerPacket(
               this,
               new Vec2f(this.rotation.x, this.pitch.getValue() ? 90.0F : PlayerPacketManager.INSTANCE.getServerSideRotation().y)
            );
            PlayerPacketManager.INSTANCE.addPacket(packet);
         }
      }
   );
   boolean switching = true;

   @Override
   public void onUpdate() {
      if (mc.player != null && mc.world != null && !EntityUtil.isDead(mc.player) && !this.inNether()) {
         for (EntityPlayer player : mc.world.playerEntities) {
            if (!(mc.player.getDistanceSq(player) > this.enemyRange.getValue() * this.enemyRange.getValue())) {
               double lastYaw = 512.0;
               int tick = this.startTick.getValue();
               if (this.playerSpeed.get(player) != null) {
                  BedAura.MoveRotation info = this.playerSpeed.get(player);
                  lastYaw = info.yaw;
                  tick = info.tick + this.addTick.getValue();
               }

               if (tick > this.maxPredict) {
                  tick = this.maxPredict;
               }

               this.playerSpeed.put(player, new BedAura.MoveRotation(player, lastYaw, tick));
            }
         }

         this.calc();
      } else {
         this.target = null;
         this.headPos = this.basePos = null;
         this.damage = this.selfDamage = 0.0F;
         this.rotation = null;
      }
   }

   @Override
   public void fast() {
      if (mc.player != null && mc.world != null && !EntityUtil.isDead(mc.player) && !this.inNether()) {
         if (this.updatetiming.passedMs(this.updateDelay.getValue().intValue())) {
            this.updatetiming.reset();
            if (!this.pause.getValue()) {
               this.burrow = false;
            } else {
               BlockPos pos = PlayerUtil.getPlayerPos();
               this.burrow = this.isBurrow(pos) && !this.isBurrow(pos.up());
            }

            this.maxPredict = this.tickPredict.getValue();
            NetHandlerPlayClient connection = mc.getConnection();
            if (this.detect.getValue() && connection != null) {
               NetworkPlayerInfo info = connection.getPlayerInfo(mc.getConnection().getGameProfile().getId());
               if (info != null) {
                  this.maxPredict = info.getResponseTime() * 2 / 50;
               }
            }

            if (this.base.getValue() && this.basetiming.passedMs(this.baseDelay.getValue().intValue())) {
               this.canBasePlace = true;
               this.basetiming.reset();
            }
         }

         if (this.continuE != null && !isPos2(this.continuE, this.basePos) && this.isBed(this.continuE)) {
            this.switching = true;
         }

         this.bedaura();
      }
   }

   private boolean isBurrow(BlockPos pos) {
      AxisAlignedBB box = BlockUtil.getBoundingBox(pos);
      if (box == null) {
         return false;
      } else if (!mc.player.boundingBox.intersects(box)) {
         return false;
      } else {
         Block block = BlockUtil.getBlock(pos);
         return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK || block == Blocks.ENDER_CHEST;
      }
   }

   private void bedaura() {
      if (!this.renderTest.getValue() && this.headPos != null && this.basePos != null) {
         if (this.target.defaultPlayer != null && !ColorMain.INSTANCE.breakList.contains(this.basePos) && !ColorMain.INSTANCE.breakList.contains(this.headPos)) {
            String var1 = this.mode.getValue();
            switch (var1) {
               case "PlaceBreak":
                  this.place(this.placeDelay.getValue());
                  this.breakBed(this.breakDelay.getValue());
                  break;
               case "BreakPlace":
                  this.breakBed(this.breakDelay.getValue());
                  this.place(this.placeDelay.getValue());
                  break;
               case "Switch":
                  if (this.switching) {
                     if (this.place(this.placeDelay.getValue()) || this.breakBed(this.breakDelay.getValue())) {
                        this.switching = false;
                     }
                  } else if (this.breakBed(this.switchBreakDelay.getValue()) || this.place(this.switchPlaceDelay.getValue())) {
                     this.switching = true;
                  }
               case "Stuck":
                  if (this.stuck(this.target)) {
                     this.breakBed(this.stuckBreakDelay.getValue());
                     this.place(this.stuckPlaceDelay.getValue());
                  } else {
                     this.place(this.placeDelay.getValue());
                     this.breakBed(this.breakDelay.getValue());
                  }
                  break;
               case "Test":
                  if (this.stuck(this.target)) {
                     this.breakBed(this.stuckBreakDelay.getValue());
                     this.place(this.stuckPlaceDelay.getValue());
                  } else if (this.switching) {
                     if (this.place(this.placeDelay.getValue()) || this.breakBed(this.breakDelay.getValue())) {
                        this.switching = false;
                     }
                  } else if (this.breakBed(this.switchBreakDelay.getValue()) || this.place(this.switchPlaceDelay.getValue())) {
                     this.switching = true;
                  }
            }
         } else {
            this.place(this.placeDelay.getValue());
            this.breakBed(this.breakDelay.getValue());
         }
      }
   }

   private void calc() {
      if (this.calctiming.passedMs(this.calcDelay.getValue().intValue())) {
         this.calctiming.reset();
         this.target = null;
         this.headPos = this.basePos = null;
         this.damage = this.selfDamage = 0.0F;
         this.rotation = null;
         boolean offhand = !this.handMode.getValue().equals("Main") && mc.player.getHeldItemOffhand().getItem() == Items.BED;
         if (!offhand && !this.handMode.getValue().equals("Off")) {
            if (this.refill.getValue()) {
               this.refill_bed();
            }

            this.slot = BurrowUtil.findHotbarBlock(ItemBed.class);
            if (this.slot == -1) {
               return;
            }
         }

         this.hand = offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
         BedAura.EntityInfo self = new BedAura.EntityInfo(mc.player, this.selfPredict.getValue());
         BedAura.PlaceInfo placeInfo = this.getPlaceInfo(self, this.findBlocksExcluding(this.base.getValue() && this.canBasePlace));
         if (placeInfo == null) {
            List<Entity> entityList = new ArrayList<>();

            for (Entity entity : mc.world.loadedEntityList) {
               if (!(mc.player.getDistance(entity) > this.enemyRange.getValue().intValue()) && !EntityUtil.isDead(entity)) {
                  if (this.monster.getValue() && EntityUtil.isMobAggressive(entity)) {
                     entityList.add(entity);
                  }

                  if (this.neutral.getValue() && EntityUtil.isNeutralMob(entity)) {
                     entityList.add(entity);
                  }

                  if (this.animal.getValue() && EntityUtil.isPassive(entity)) {
                     entityList.add(entity);
                  }
               }
            }

            placeInfo = this.calculatePlacement(this.getNearestEntity(entityList), self, this.findBlocksExcluding(true));
            this.target = placeInfo.target;
         } else {
            this.target = placeInfo.target;
            if (ModuleManager.isModuleEnabled("AutoEz")) {
               AutoEz.INSTANCE.addTargetedPlayer(this.target.defaultPlayer.getName());
            }

            if (this.base.getValue() && placeInfo.basePos != null) {
               this.canBasePlace = false;
               int obbySlot = BurrowUtil.findHotbarBlock(BlockObsidian.class);
               BlockPos pos = placeInfo.basePos;
               InventoryUtil.run(
                  obbySlot,
                  this.packetSwitch.getValue(),
                  () -> BurrowUtil.placeBlock(pos, EnumHand.MAIN_HAND, false, this.packetBase.getValue(), false, this.baseSwing.getValue())
               );
            }
         }

         BlockPos bedPos = placeInfo.placePos;
         if (bedPos == null) {
            return;
         }

         this.damage = placeInfo.damage;
         this.selfDamage = placeInfo.selfDamage;
         this.headPos = bedPos;
         switch (RotationUtil.getFacing(PlayerPacketManager.INSTANCE.getServerSideRotation().x)) {
            case SOUTH:
               this.face = "SOUTH";
               this.rotation = new Vec2f(0.0F, 90.0F);
               bedPos = new BlockPos(this.headPos.x, this.headPos.y, this.headPos.z - 1);
               break;
            case WEST:
               this.face = "WEST";
               this.rotation = new Vec2f(90.0F, 90.0F);
               bedPos = new BlockPos(this.headPos.x + 1, this.headPos.y, this.headPos.z);
               break;
            case NORTH:
               this.face = "NORTH";
               this.rotation = new Vec2f(180.0F, 90.0F);
               bedPos = new BlockPos(this.headPos.x, this.headPos.y, this.headPos.z + 1);
               break;
            case EAST:
               this.face = "EAST";
               this.rotation = new Vec2f(-90.0F, 90.0F);
               bedPos = new BlockPos(this.headPos.x - 1, this.headPos.y, this.headPos.z);
         }

         if (!this.block(bedPos, true, true)) {
            if (!this.autorotate.getValue() || this.burrow) {
               this.target = null;
               this.headPos = this.basePos = null;
               this.damage = this.selfDamage = 0.0F;
               this.rotation = null;
               return;
            }

            if (this.block(this.headPos.east(), true, true)) {
               this.face = "WEST";
               this.rotation = new Vec2f(90.0F, 90.0F);
               bedPos = new BlockPos(this.headPos.x + 1, this.headPos.y, this.headPos.z);
            } else if (this.block(this.headPos.north(), true, true)) {
               this.face = "SOUTH";
               this.rotation = new Vec2f(0.0F, 90.0F);
               bedPos = new BlockPos(this.headPos.x, this.headPos.y, this.headPos.z - 1);
            } else if (this.block(this.headPos.west(), true, true)) {
               this.face = "EAST";
               this.rotation = new Vec2f(-90.0F, 90.0F);
               bedPos = new BlockPos(this.headPos.x - 1, this.headPos.y, this.headPos.z);
            } else {
               if (!this.block(this.headPos.south(), true, true)) {
                  this.target = null;
                  this.headPos = this.basePos = null;
                  this.damage = this.selfDamage = 0.0F;
                  this.rotation = null;
                  return;
               }

               this.face = "NORTH";
               this.rotation = new Vec2f(180.0F, 90.0F);
               bedPos = new BlockPos(this.headPos.x, this.headPos.y, this.headPos.z + 1);
            }
         }

         this.headPos = this.headPos.up();
         this.basePos = bedPos.up();
      }
   }

   private boolean place(int delay) {
      if (!this.checkBed.getValue() || !this.isBed(this.headPos) && !this.isBed(this.basePos)) {
         if (!this.placetiming.passedMs(this.getPlaceDelay(delay))) {
            return false;
         } else {
            if (this.continuE == null || this.continuE.distanceSq(this.basePos) > 14.0 || BlockUtil.getBlock(this.continuE) != Blocks.BED) {
               this.continuE = this.basePos;
            }

            BlockPos neighbour = this.basePos.down();
            EnumFacing opposite = EnumFacing.DOWN.getOpposite();
            Vec3d hitVec = new Vec3d(neighbour).add(0.5, 0.5, 0.5).add(new Vec3d(opposite.getDirectionVec()).scale(0.5));
            if (BlockUtil.blackList.contains(mc.world.getBlockState(neighbour).getBlock()) && !ColorMain.INSTANCE.sneaking) {
               mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, Action.START_SNEAKING));
            }

            this.run(() -> {
               if (this.packetPlace.getValue()) {
                  mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(neighbour, EnumFacing.UP, this.hand, 0.5F, 1.0F, 0.5F));
               } else {
                  mc.playerController.processRightClickBlock(mc.player, mc.world, neighbour, EnumFacing.UP, hitVec, this.hand);
               }
            }, this.slot);
            if (this.placeSwing.getValue()) {
               this.swing(this.hand);
            }

            this.placetiming.reset();
            return true;
         }
      } else {
         return true;
      }
   }

   private void run(Runnable runnable, int slot) {
      if (this.hand == EnumHand.OFF_HAND) {
         runnable.run();
      } else {
         int oldslot = mc.player.inventory.currentItem;
         if (slot >= 0 && slot != oldslot) {
            if (this.packetSwitch.getValue()) {
               InventoryUtil.packetSwitch(slot);
            } else {
               InventoryUtil.switchSlot(slot);
            }

            runnable.run();
            if (this.silentSwitch.getValue()) {
               if (this.packetSwitch.getValue()) {
                  InventoryUtil.packetSwitch(oldslot);
               } else {
                  InventoryUtil.switchSlot(oldslot);
               }
            }

            mc.player.openContainer.detectAndSendChanges();
         } else {
            runnable.run();
         }
      }
   }

   private boolean breakBed(int delay) {
      if (this.breaktiming.passedMs(this.getBreakDelay(delay))) {
         EnumFacing side = EnumFacing.UP;
         Vec3d facing = this.getHitVecOffset(side);
         if (ModuleManager.getModule(ColorMain.class).sneaking) {
            mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, Action.STOP_SNEAKING));
         }

         mc.player
            .connection
            .sendPacket(
               new CPacketPlayerTryUseItemOnBlock(
                  this.basePos, side, this.hand, (float)facing.x, (float)facing.y, (float)facing.z
               )
            );
         if (this.isBed(this.headPos) && !this.isBed(this.basePos)) {
            mc.player
               .connection
               .sendPacket(
                  new CPacketPlayerTryUseItemOnBlock(
                     this.headPos, side, this.hand, (float)facing.x, (float)facing.y, (float)facing.z
                  )
               );
         }

         if (this.breakSwing.getValue()) {
            this.swing(this.hand);
         }

         this.breaktiming.reset();
         return true;
      } else {
         return false;
      }
   }

   private BedAura.PlaceInfo getPlaceInfo(BedAura.EntityInfo self, List<BlockPos> posList) {
      BedAura.PlaceInfo placeInfo = null;
      List<EntityPlayer> playerList = PlayerUtil.getNearPlayers(this.enemyRange.getValue().intValue(), this.maxEnemies.getValue());
      String var5 = this.targetMode.getValue();
      switch (var5) {
         case "Nearest":
            EntityPlayer entityPlayer = playerList.stream().min(Comparator.comparing(p -> mc.player.getDistance(p))).orElse(null);
            if (entityPlayer != null) {
               BedAura.EntityInfo player = new BedAura.EntityInfo(entityPlayer, this.predict.getValue());
               placeInfo = this.calculateBestPlacement(player, self, posList);
            }
            break;
         case "Damage":
            BedAura.PlaceInfo bestx = null;

            for (EntityPlayer entityPlayerxxxx : playerList) {
               if (entityPlayerxxxx != null) {
                  BedAura.EntityInfo player = new BedAura.EntityInfo(entityPlayerxxxx, this.predict.getValue());
                  BedAura.PlaceInfo info = this.calculateBestPlacement(player, self, posList);
                  if (bestx == null || info.damage > bestx.damage) {
                     bestx = info;
                  }
               }
            }

            placeInfo = bestx;
            break;
         case "Health":
            double health = 37.0;
            EntityPlayer player = null;

            for (EntityPlayer entityPlayerx : playerList) {
               if (player == null || health > entityPlayerx.getHealth() + entityPlayerx.getAbsorptionAmount()) {
                  player = entityPlayerx;
                  health = entityPlayerx.getHealth() + entityPlayerx.getAbsorptionAmount();
               }
            }

            if (player != null) {
               placeInfo = this.calculateBestPlacement(new BedAura.EntityInfo(player, this.predict.getValue()), self, posList);
            }
            break;
                  case "Smart": { // Sử dụng ngoặc nhọn để cô lập các biến cục bộ (tránh lỗi already defined)
            List<EntityPlayer> players = new ArrayList<>();

            // Lọc danh sách người chơi dựa trên máu
            for (EntityPlayer entityPlayerxx : playerList) {
               if (this.smartHealth.getValue() >= entityPlayerxx.getHealth() + entityPlayerxx.getAbsorptionAmount()) {
                  players.add(entityPlayerxx);
               }
            }

            // Tìm mục tiêu có lượng máu thấp nhất
            EntityPlayer target = players.stream()
                .min(Comparator.comparing(p -> p.getHealth() + p.getAbsorptionAmount()))
                .orElse(null);
            
            BedAura.PlaceInfo best = null;

            if (target != null) {
               // Chuyển đổi target sang EntityInfo trước khi truyền vào hàm
               BedAura.EntityInfo entityInfo = new BedAura.EntityInfo(target, this.predict.getValue());
               // SỬA: Truyền entityInfo vào thay vì player
               best = this.calculateBestPlacement(entityInfo, self, posList);
            }

            // Nếu chưa tìm được vị trí tốt nhất, duyệt toàn bộ danh sách người chơi
            if (best == null) {
               for (EntityPlayer entityPlayerxxx : playerList) {
                  if (entityPlayerxxx != null) {
                     // Tạo targetInfo từ player đang duyệt
                     BedAura.EntityInfo targetInfo = new BedAura.EntityInfo(entityPlayerxxx, this.predict.getValue());
                     // SỬA: Truyền targetInfo vào thay vì player
                     BedAura.PlaceInfo info = this.calculateBestPlacement(targetInfo, self, posList);
                     
                     if (best == null || info.damage > best.damage) {
                        best = info;
                     }
                  }
               }
            }

            placeInfo = best;
            break; // Kết thúc case Smart
         }
      } // Dấu đóng của lệnh switch

      return placeInfo;
   }

   private List<BlockPos> findBlocksExcluding(boolean calcWithOutBase) {
      return EntityUtil.getSphere(PlayerUtil.getEyesPos(), this.range.getValue() + 1.0, this.yRange.getValue(), false, false, 0)
         .stream()
         .filter(pos -> this.canPlaceBed(pos, !calcWithOutBase))
         .collect(Collectors.toList());
   }

   private boolean canFacePlace(BedAura.EntityInfo target) {
      if (target.hp <= this.facePlaceValue.getValue().intValue()) {
         return true;
      } else {
         for (ItemStack itemStack : target.defaultPlayer.getArmorInventoryList()) {
            if (!itemStack.isEmpty() && itemStack.getCount() <= this.armorRate.getValue()) {
               float dmg = ((float)itemStack.getMaxDamage() - itemStack.getItemDamage()) / itemStack.getMaxDamage();
               if (dmg < this.armorRate.getValue().intValue() / 100.0F) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private BedAura.PlaceInfo calculateBestPlacement(BedAura.EntityInfo target, BedAura.EntityInfo self, List<BlockPos> blocks) {
      BedAura.PlaceInfo best = new BedAura.PlaceInfo(
         target, null, (float)Math.min(Math.min(this.minDmg.getValue(), this.slowMinDmg.getValue()), this.fpMinDmg.getMin()), -1.0F, null
      );
      if (target != null && self != null) {
         boolean facePlace = this.canFacePlace(target);

         for (BlockPos pos : blocks) {
            BlockPos basePos = null;
            boolean air = BlockUtil.canReplace(pos);
            boolean canPlace = this.highVersion.getValue() || !air && !this.needBase(pos);
            if (!canPlace) {
               if (!this.base.getValue()
                  || best.damage >= this.toggleDmg.getValue() && best.basePos == null
                  || pos.getY() + 1 > target.player.posY + this.maxY.getValue()
                  || BurrowUtil.findHotbarBlock(BlockObsidian.class) == -1
                  || LemonClient.speedUtil.getPlayerSpeed(target.defaultPlayer) > this.maxSpeed.getValue()) {
                  continue;
               }

               basePos = this.getBestBasePos(pos);
               if (basePos == null) {
                  continue;
               }
            }

            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.5625;
            double z = pos.getZ() + 0.5;
            float targetDamage = DamageUtil.calculateDamage(target.defaultPlayer, target.position, target.boundingBox, x, y, z, 5.0F, "Bed");
            if ((canPlace || !(targetDamage < this.baseMinDmg.getValue()) && targetDamage != best.damage)
               && !(targetDamage < best.damage)
               && (
                  facePlace
                     ? !(targetDamage < this.fpMinDmg.getValue())
                     : !(targetDamage < this.minDmg.getValue()) || !(targetDamage < this.slowMinDmg.getValue()) && this.slowFP.getValue()
               )) {
               float selfDamage = 0.0F;
               if (!self.player.isCreative()) {
                  selfDamage = DamageUtil.calculateDamage(self.defaultPlayer, self.position, self.boundingBox, x, y, z, 5.0F, "Bed");
                  if (selfDamage + this.balance.getValue() > this.maxSelfDmg.getValue()
                        && (targetDamage >= target.hp ? !this.forcePlace.getValue() : !this.ignore.getValue())
                     || this.suicide.getValue() && selfDamage + this.balance.getValue() >= self.hp) {
                     continue;
                  }
               }

               best = new BedAura.PlaceInfo(target, pos, targetDamage, selfDamage, basePos);
            }
         }

         return best;
      } else {
         return best;
      }
   }

   private BedAura.PlaceInfo calculatePlacement(EntityLivingBase target, BedAura.EntityInfo self, List<BlockPos> poslist) {
      BedAura.PlaceInfo best = new BedAura.PlaceInfo(
         new BedAura.EntityInfo(target),
         null,
         (float)Math.min(Math.min(this.minDmg.getValue(), this.slowMinDmg.getValue()), this.fpMinDmg.getMin()),
         -1.0F,
         null
      );
      if (target != null && self != null) {
         for (BlockPos pos : poslist) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.5625;
            double z = pos.getZ() + 0.5;
            float targetDamage = DamageUtil.calculateDamage(target, target.getPositionVector(), target.boundingBox, x, y, z, 5.0F, "Bed");
            float selfDamage = DamageUtil.calculateDamage(self.defaultPlayer, self.position, self.boundingBox, x, y, z, 5.0F, "Bed");
            if ((
                  !(targetDamage < this.minDmg.getValue())
                     || !(targetDamage < this.slowMinDmg.getValue()) && this.slowFP.getValue()
                     || !(targetDamage < this.fpMinDmg.getValue())
               )
               && (
                  self.player.isCreative()
                     || (!(selfDamage + this.balance.getValue() > this.maxSelfDmg.getValue()) || this.ignore.getValue())
                        && (!this.suicide.getValue() || !(selfDamage + this.balance.getValue() >= self.hp))
               )
               && targetDamage > best.damage) {
               best = new BedAura.PlaceInfo(new BedAura.EntityInfo(target), pos, targetDamage, selfDamage, null);
            }
         }

         return best;
      } else {
         return best;
      }
   }

   private boolean near(BedAura.EntityInfo player) {
      AxisAlignedBB box = player.defaultPlayer.boundingBox;
      if (box.intersects(this.bedBoundingBox(this.basePos)) && box.intersects(this.bedBoundingBox(this.basePos))) {
         return false;
      } else {
         boolean near = (int)(player.defaultPlayer.posY + 0.5) + 2 >= this.headPos.y
            && (
               player.defaultPlayer.getDistance(this.headPos.getX() + 0.5, this.headPos.getY() + 0.25, this.headPos.getZ() + 0.5)
                     < 2.5
                  || player.defaultPlayer
                        .getDistance(this.basePos.getX() + 0.5, player.defaultPlayer.posY, this.basePos.getZ() + 0.5)
                     < 2.5
            )
            && player.defaultPlayer.getDistance(mc.player) <= 6.0F;
         boolean predictNear = player.player.posY > this.headPos.y
            && (
               player.player.getDistance(this.headPos.getX() + 0.5, this.headPos.getY() + 0.25, this.headPos.getZ() + 0.5) < 2.5
                  || player.player.getDistance(this.basePos.getX() + 0.5, player.player.posY, this.basePos.getZ() + 0.5) < 1.5
            )
            && player.player.getDistance(mc.player) <= 6.0F;
         return near || predictNear;
      }
   }

   private boolean stuck(EntityPlayer player) {
      return player.posY - (int)player.posY > 0.3;
   }

   private boolean stuck(BedAura.EntityInfo target) {
      EntityPlayer player = target.defaultPlayer;
      EntityPlayer predict = target.player;
      boolean inAir = true;

      for (Vec3d vec3d : new Vec3d[]{new Vec3d(0.25, 0.0, 0.25), new Vec3d(0.25, 0.0, -0.25), new Vec3d(-0.25, 0.0, 0.25), new Vec3d(-0.25, 0.0, -0.25)}) {
         BlockPos pos = new BlockPos(player.posX + vec3d.x, player.posY + 0.7, player.posZ + vec3d.z);
         pos = pos.down();
         if (!BlockUtil.canReplace(pos) && BlockUtil.getBlock(pos) != Blocks.BED) {
            inAir = false;
            break;
         }
      }

      double y = predict.posY - player.posY;
      return this.near(target) && (this.stuck(player) || this.stuck(predict) || inAir || y > 0.5 || y < -0.5);
   }

   private AxisAlignedBB bedBoundingBox(BlockPos pos) {
      return new AxisAlignedBB(
         pos.x, pos.y, pos.z, pos.x + 1, pos.y + 0.4, pos.z + 1
      );
   }

   private int getPlaceDelay(int value) {
      return this.damage < this.minDmg.getValue() ? this.slowPlaceDelay.getValue() : value;
   }

   private int getBreakDelay(int value) {
      return this.damage < this.minDmg.getValue() ? this.slowBreakDelay.getValue() : value;
   }

   private EntityLivingBase getNearestEntity(List<Entity> list) {
      return (EntityLivingBase)list.stream()
         .filter(target -> target instanceof EntityLivingBase)
         .min(Comparator.comparing(p -> mc.player.getDistance(p)))
         .orElse(null);
   }

   private boolean canPlaceBed(BlockPos blockPos, boolean baseCheck) {
      if (!this.block(blockPos, !this.highVersion.getValue() || !this.allPossible.getValue() || baseCheck, false)) {
         return false;
      } else if (this.autorotate.getValue() && !this.burrow) {
         for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.UP && facing != EnumFacing.DOWN) {
               BlockPos pos = blockPos.offset(facing);
               if (this.block(pos, this.highVersion.getValue() && !this.placeInAir.getValue() || baseCheck, true)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         BlockPos pos = blockPos.offset(RotationUtil.getFacing(PlayerPacketManager.INSTANCE.getServerSideRotation().x), -1);
         return this.block(pos, this.highVersion.getValue() && !this.placeInAir.getValue() || baseCheck, true) && this.inRange(pos.up());
      }
   }

   private boolean canPlaceBase(BlockPos pos) {
      if (this.detectBreak.getValue() && ColorMain.INSTANCE.breakList.contains(pos)) {
         return false;
      } else if (!this.inRange(pos)) {
         return false;
      } else {
         return BurrowUtil.getBedFacing(pos) == null ? false : this.space(pos.up()) && !this.intersectsWithEntity(pos);
      }
   }

   private boolean needBase(BlockPos pos) {
      for (BlockPos side : this.sides) {
         BlockPos blockPos = pos.add(side);
         if (this.space(blockPos.up()) && this.inRange(blockPos.up()) && !BlockUtil.canReplace(blockPos) && this.solid(pos)) {
            return false;
         }
      }

      return true;
   }

   private BlockPos getBestBasePos(BlockPos pos) {
      BlockPos bestPos = null;
      double bestRange = 1000.0;
      if (this.autorotate.getValue() && !this.burrow) {
         for (BlockPos side : this.sides) {
            BlockPos base = pos.add(side);
            if (this.canPlaceBase(base) && !this.intersectsWithEntity(pos) && (bestPos == null || bestRange > mc.player.getDistanceSq(base))) {
               bestRange = mc.player.getDistanceSq(base);
               bestPos = base;
            }
         }

         return bestPos;
      } else {
         BlockPos base = pos.offset(RotationUtil.getFacing(PlayerPacketManager.INSTANCE.getServerSideRotation().x), -1);
         return this.canPlaceBase(base) ? base : null;
      }
   }

   private boolean intersectsWithEntity(BlockPos pos) {
      for (Entity entity : mc.world.loadedEntityList) {
         if (!(entity instanceof EntityItem) && new AxisAlignedBB(pos).intersects(entity.getEntityBoundingBox())) {
            return true;
         }
      }

      return false;
   }

   private boolean block(BlockPos pos, boolean baseCheck, boolean rangeCheck) {
      if (!this.space(pos.up())) {
         return false;
      } else {
         if (BlockUtil.canReplace(pos)) {
            if (baseCheck || !this.canPlaceBase(pos)) {
               return false;
            }
         } else if (!this.highVersion.getValue() && !this.solid(pos)) {
            return false;
         }

         return !rangeCheck || this.inRange(pos.up());
      }
   }

   private boolean isBed(BlockPos pos) {
      Block block = mc.world.getBlockState(pos).getBlock();
      return block == Blocks.BED || block instanceof BlockBed;
   }

   private boolean space(BlockPos pos) {
      return mc.world.isAirBlock(pos) || mc.world.getBlockState(pos).getBlock() == Blocks.BED;
   }

   private boolean solid(BlockPos pos) {
      return !BlockUtil.isBlockUnSolid(pos)
         && !(mc.world.getBlockState(pos).getBlock() instanceof BlockBed)
         && mc.world.getBlockState(pos).isSideSolid(mc.world, pos, EnumFacing.UP)
         && BlockUtil.getBlock(pos).fullBlock;
   }

   private boolean inRange(BlockPos pos) {
      double x = pos.x - mc.player.posX;
      double z = pos.z - mc.player.posZ;
      double y = pos.y - PlayerUtil.getEyesPos().y;
      double add = Math.sqrt(y * y) / 2.0;
      return x * x + z * z <= (this.range.getValue() - add) * (this.range.getValue() - add) && y * y <= this.yRange.getValue() * this.yRange.getValue();
   }

   private static boolean isPos2(BlockPos pos1, BlockPos pos2) {
      return pos1 != null && pos2 != null
         ? pos1.x == pos2.x && pos1.y == pos2.y && pos1.z == pos2.z
         : false;
   }

   public void refill_bed() {
      if (!(mc.currentScreen instanceof GuiContainer) || mc.currentScreen instanceof GuiInventory) {
         int airSlot = this.isSpace();
         if (airSlot != -1) {
            for (int i = 9; i < 36; i++) {
               if (mc.player.inventory.getStackInSlot(i).getItem() == Items.BED) {
                  if (this.clickMode.getValue().equalsIgnoreCase("Quick")) {
                     if (mc.player.inventory.getStackInSlot(airSlot).getItem() != Items.AIR) {
                        mc.playerController.windowClick(mc.player.inventoryContainer.windowId, airSlot + 36, 0, ClickType.QUICK_MOVE, mc.player);
                     }

                     mc.playerController.windowClick(mc.player.inventoryContainer.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                  } else if (this.clickMode.getValue().equalsIgnoreCase("Swap")) {
                     mc.playerController.windowClick(0, i, airSlot, ClickType.SWAP, mc.player);
                  } else {
                     mc.playerController.windowClick(mc.player.inventoryContainer.windowId, i, 0, ClickType.PICKUP, mc.player);
                     mc.playerController.windowClick(mc.player.inventoryContainer.windowId, airSlot + 36, 0, ClickType.PICKUP, mc.player);
                  }
                  break;
               }
            }
         }
      }
   }

   private int isSpace() {
      int slot = -1;
      if (this.force.getValue()) {
         if (this.refillMode.getValue().equals("Only")) {
            int slot1 = this.slotS.getValue() - 1;
            if (mc.player.inventory.getStackInSlot(slot1).getItem() != Items.BED) {
               slot = slot1;
            }
         } else {
            for (int i = 0; i < 9; i++) {
               if (mc.player.inventory.getStackInSlot(i).getItem() != Items.BED) {
                  slot = i;
               }
            }
         }
      } else if (this.refillMode.getValue().equals("Only")) {
         int slot1 = this.slotS.getValue() - 1;
         if (mc.player.inventory.getStackInSlot(slot1).getItem() == Items.AIR) {
            slot = slot1;
         }
      } else {
         for (int ix = 0; ix < 9; ix++) {
            if (mc.player.inventory.getStackInSlot(ix).getItem() == Items.AIR) {
               slot = ix;
            }
         }
      }

      return slot;
   }

   private Vec3d getHitVecOffset(EnumFacing face) {
      Vec3i vec = face.getDirectionVec();
      return new Vec3d(vec.x * 0.5F + 0.5F, vec.y * 0.5F + 0.5F, vec.z * 0.5F + 0.5F);
   }

   private void swing(EnumHand hand) {
      if (this.packetSwing.getValue()) {
         mc.player.connection.sendPacket(new CPacketAnimation(hand));
      } else {
         mc.player.swingArm(hand);
      }
   }

   private boolean inNether() {
      return mc.player.dimension == 0;
   }

   @Override
   public void onEnable() {
      this.calctiming.reset();
      this.basetiming.reset();
      this.placetiming.reset();
      this.breaktiming.reset();
      this.updatetiming.reset();
      this.continuE = null;
      this.switching = true;
      this.updateTimeBase = System.currentTimeMillis();
      this.updateTimeHead = System.currentTimeMillis();
      this.startTime = System.currentTimeMillis();
      this.lastBestBase = null;
      this.lastBestHead = null;
      this.movingBaseNow = new Vec3d(-1.0, -1.0, -1.0);
      this.movingHeadNow = new Vec3d(-1.0, -1.0, -1.0);
   }

   @Override
   public void onDisable() {
      this.headPos = null;
      this.basePos = null;
   }

   @Override
   public void onWorldRender(RenderEvent event) {
      if (mc.world != null && mc.player != null) {
         BlockPos nowBase = this.basePos;
         BlockPos nowHead = this.headPos;
         if (nowBase != this.lastBestBase) {
            if (this.basePos != null && this.lastBestBase == null) {
               this.movingBaseNow = new Vec3d(this.basePos.getX(), this.basePos.getY(), this.basePos.getZ());
            }

            this.updateTimeBase = System.currentTimeMillis();
            if (this.basePos == null) {
               this.startTime = System.currentTimeMillis();
            } else if (this.lastBestBase == null) {
               this.startTime = System.currentTimeMillis();
            }

            this.lastBestBase = this.basePos;
         }

         if (nowHead != this.lastBestHead) {
            if (this.headPos != null && this.lastBestHead == null) {
               this.movingHeadNow = new Vec3d(this.headPos.getX(), this.headPos.getY(), this.headPos.getZ());
            }

            this.updateTimeHead = System.currentTimeMillis();
            this.lastBestHead = this.headPos;
         }

         if (this.lastBestBase != null) {
            if (this.movingBaseNow.x == -1.0 && this.movingBaseNow.y == -1.0 && this.movingBaseNow.z == -1.0) {
               this.movingBaseNow = new Vec3d(this.lastBestBase.getX(), this.lastBestBase.getY(), this.lastBestBase.getZ());
            }

            if (this.movingTime.getValue() == 0) {
               this.movingBaseNow = new Vec3d(this.lastBestBase);
            } else {
               this.movingBaseNow = new Vec3d(
                  this.movingBaseNow.x
                     + (this.lastBestBase.getX() - this.movingBaseNow.x)
                        * this.toDelta(this.updateTimeBase, this.movingTime.getValue().intValue()),
                  this.movingBaseNow.y
                     + (this.lastBestBase.getY() - this.movingBaseNow.y)
                        * this.toDelta(this.updateTimeBase, this.movingTime.getValue().intValue()),
                  this.movingBaseNow.z
                     + (this.lastBestBase.getZ() - this.movingBaseNow.z)
                        * this.toDelta(this.updateTimeBase, this.movingTime.getValue().intValue())
               );
            }

            if (this.movingHeadNow.x == -1.0 && this.movingHeadNow.y == -1.0 && this.movingHeadNow.z == -1.0) {
               this.movingHeadNow = new Vec3d(this.lastBestHead.getX(), this.lastBestHead.getY(), this.lastBestHead.getZ());
            }

            if (this.movingTime.getValue() == 0) {
               this.movingHeadNow = new Vec3d(this.lastBestHead);
            } else {
               this.movingHeadNow = new Vec3d(
                  this.movingHeadNow.x
                     + (this.lastBestHead.getX() - this.movingHeadNow.x)
                        * this.toDelta(this.updateTimeHead, this.movingTime.getValue().intValue()),
                  this.movingHeadNow.y
                     + (this.lastBestHead.getY() - this.movingHeadNow.y)
                        * this.toDelta(this.updateTimeHead, this.movingTime.getValue().intValue()),
                  this.movingHeadNow.z
                     + (this.lastBestHead.getZ() - this.movingHeadNow.z)
                        * this.toDelta(this.updateTimeHead, this.movingTime.getValue().intValue())
               );
            }
         }

         if (this.movingBaseNow.x != -1.0 || this.movingBaseNow.y != -1.0 || this.movingBaseNow.z != -1.0) {
            this.drawBoxMain(
               this.movingBaseNow.x,
               this.movingBaseNow.y,
               this.movingBaseNow.z,
               this.movingHeadNow.x,
               this.movingHeadNow.y,
               this.movingHeadNow.z
            );
         }
      }
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

   private void drawAnimationRender(AxisAlignedBB box1, AxisAlignedBB box2) {
      float size;
      if (this.basePos == null) {
         size = 1.0F - this.toDelta(this.startTime, this.lifeTime.getValue().intValue());
      } else {
         size = this.toDelta(this.startTime, this.lifeTime.getValue().intValue());
      }

      int alpha = (int)(this.alpha.getValue().intValue() * size);
      int outAlpha = (int)(this.outAlpha.getValue().intValue() * size);
      GSColor baseColor = new GSColor(this.color.getValue(), alpha);
      GSColor baseOutColor = new GSColor(this.color.getValue(), outAlpha);
      GSColor headColor = new GSColor(this.color2.getValue(), alpha);
      GSColor headOutColor = new GSColor(this.color2.getValue(), outAlpha);
      AxisAlignedBB box = new AxisAlignedBB(
         Math.min(box1.minX, box2.minX),
         box1.minY,
         Math.min(box1.minZ, box2.minZ),
         Math.max(box1.maxX, box2.maxX),
         box1.maxY,
         Math.max(box1.maxZ, box2.maxZ)
      );
      if (baseColor.equals(headColor)) {
         RenderUtil.drawBox(box, false, 0.5625, baseColor, 63);
         RenderUtil.drawBoundingBox(box, this.width.getValue().intValue(), baseOutColor);
      } else {
         String damageText = this.face;
         switch (damageText) {
            case "WEST":
               if (this.gradient.getValue()) {
                  RenderUtil.drawBoxDire(box, 0.5625, baseColor, 0, 16);
                  RenderUtil.drawBoxDire(box, 0.5625, headColor, 0, 32);
               } else {
                  RenderUtil.drawBox(box2, false, 0.5625, baseColor, 31);
                  RenderUtil.drawBox(box1, false, 0.5625, headColor, 47);
               }

               if (this.outGradient.getValue()) {
                  RenderUtil.drawBoundingBoxDire(box, 0.5625, (double)this.width.getValue().intValue(), baseOutColor, 0, 16);
                  RenderUtil.drawBoundingBoxDire(box, 0.5625, (double)this.width.getValue().intValue(), headOutColor, 0, 32);
               }
               break;
            case "EAST":
               if (this.gradient.getValue()) {
                  RenderUtil.drawBoxDire(box, 0.5625, baseColor, 0, 32);
                  RenderUtil.drawBoxDire(box, 0.5625, headColor, 0, 16);
               } else {
                  RenderUtil.drawBox(box2, false, 0.5625, baseColor, 47);
                  RenderUtil.drawBox(box1, false, 0.5625, headColor, 31);
               }

               if (this.outGradient.getValue()) {
                  RenderUtil.drawBoundingBoxDire(box, 0.5625, (double)this.width.getValue().intValue(), baseOutColor, 0, 32);
                  RenderUtil.drawBoundingBoxDire(box, 0.5625, (double)this.width.getValue().intValue(), headOutColor, 0, 16);
               }
               break;
            case "SOUTH":
               if (this.gradient.getValue()) {
                  RenderUtil.drawBoxDire(box, 0.5625, baseColor, 0, 8);
                  RenderUtil.drawBoxDire(box, 0.5625, headColor, 0, 4);
               } else {
                  RenderUtil.drawBox(box2, false, 0.5625, baseColor, 59);
                  RenderUtil.drawBox(box1, false, 0.5625, headColor, 55);
               }

               if (this.outGradient.getValue()) {
                  RenderUtil.drawBoundingBoxDire(box, 0.5625, (double)this.width.getValue().intValue(), baseOutColor, 0, 8);
                  RenderUtil.drawBoundingBoxDire(box, 0.5625, (double)this.width.getValue().intValue(), headOutColor, 0, 4);
               }
               break;
            case "NORTH":
               if (this.gradient.getValue()) {
                  RenderUtil.drawBoxDire(box, 0.5625, baseColor, 0, 4);
                  RenderUtil.drawBoxDire(box, 0.5625, headColor, 0, 8);
               } else {
                  RenderUtil.drawBox(box2, false, 0.5625, baseColor, 55);
                  RenderUtil.drawBox(box1, false, 0.5625, headColor, 59);
               }

               if (this.outGradient.getValue()) {
                  RenderUtil.drawBoundingBoxDire(box, 0.5625, (double)this.width.getValue().intValue(), baseOutColor, 0, 4);
                  RenderUtil.drawBoundingBoxDire(box, 0.5625, (double)this.width.getValue().intValue(), headOutColor, 0, 8);
               }
         }

         if (!this.outGradient.getValue()) {
            RenderUtil.drawBoundingBox(box2, this.width.getValue().intValue(), baseOutColor);
            RenderUtil.drawBoundingBox(box1, this.width.getValue().intValue(), headOutColor);
         }
      }

      if (this.showDamage.getValue() && this.basePos != null) {
         String[] damageText = new String[]{String.format("%.1f", this.damage)};
         if (this.showSelfDamage.getValue()) {
            damageText = new String[]{String.format("%.1f", this.damage) + "/" + String.format("%.1f", this.selfDamage)};
         }

         RenderUtil.drawNametag(
            box2.minX + 0.5,
            box2.minY + 0.28125,
            box2.minZ + 0.5,
            damageText,
            new GSColor(255, 255, 255),
            1,
            0.02666666666666667,
            0.0
         );
      }
   }

   void drawBoxMain(double x, double y, double z, double x2, double y2, double z2) {
      AxisAlignedBB box = new AxisAlignedBB(x, y, z, x + 1.0, y + 0.5625, z + 1.0);
      AxisAlignedBB box2 = new AxisAlignedBB(x2, y2, z2, x2 + 1.0, y2 + 0.5625, z2 + 1.0);
      this.drawAnimationRender(box, box2);
   }

   @Override
   public String getHudInfo() {
      Entity currentTarget = null;
      if (this.target != null) {
         currentTarget = (Entity)(this.target.defaultPlayer == null ? this.target.entity : this.target.defaultPlayer);
      }

      boolean isNull = currentTarget == null;
      String var3 = this.hudDisplay.getValue();
      switch (var3) {
         case "Target":
            return isNull
               ? "[" + ChatFormatting.WHITE + "None" + ChatFormatting.GRAY + "]"
               : "[" + ChatFormatting.WHITE + currentTarget.getName() + ChatFormatting.GRAY + "]";
         case "Damage":
            return "["
               + ChatFormatting.WHITE
               + String.format("%.1f", this.damage)
               + (this.hudSelfDamage.getValue() ? " Self: " + String.format("%.1f", this.selfDamage) : "")
               + ChatFormatting.GRAY
               + "]";
         case "Both":
            return "["
               + ChatFormatting.WHITE
               + (isNull ? "None" : currentTarget.getName())
               + " "
               + String.format("%.1f", this.damage)
               + (this.hudSelfDamage.getValue() ? " Self: " + String.format("%.1f", this.selfDamage) : "")
               + ChatFormatting.GRAY
               + "]";
         default:
            return "";
      }
   }

   class EntityInfo {
      EntityPlayer player = null;
      EntityPlayer defaultPlayer = null;
      Vec3d position;
      AxisAlignedBB boundingBox;
      EntityLivingBase entity = null;
      double hp;

      public EntityInfo(EntityPlayer player, boolean predict) {
         if (player != null) {
            this.defaultPlayer = player;
            this.player = predict
               ? PredictUtil.predictPlayer(
                  player,
                  new PredictUtil.PredictSettings(
                     BedAura.this.playerSpeed.get(player).tick,
                     BedAura.this.calculateYPredict.getValue(),
                     BedAura.this.startDecrease.getValue(),
                     BedAura.this.exponentStartDecrease.getValue(),
                     BedAura.this.decreaseY.getValue(),
                     BedAura.this.exponentDecreaseY.getValue(),
                     BedAura.this.splitXZ.getValue(),
                     BedAura.this.manualOutHole.getValue(),
                     BedAura.this.aboveHoleManual.getValue(),
                     BedAura.this.stairPredict.getValue(),
                     BedAura.this.nStair.getValue(),
                     BedAura.this.speedActivationStair.getValue()
                  )
               )
               : player;
            this.position = this.player.getPositionVector();
            this.boundingBox = this.player.getEntityBoundingBox();
            this.hp = player.getHealth() + player.getAbsorptionAmount();
         }
      }

      public EntityInfo(EntityLivingBase entity) {
         if (entity != null) {
            this.entity = entity;
            this.hp = entity.getHealth() + entity.getAbsorptionAmount();
         }
      }
   }

   class MoveRotation {
      double yaw;
      double lastYaw;
      int tick;

      public MoveRotation(EntityPlayer player, double lastYaw, int tick) {
         this.yaw = RotationUtil.getRotationTo(player.getPositionVector(), new Vec3d(player.prevPosX, player.prevPosY, player.prevPosZ)).x;
         this.lastYaw = lastYaw;
         double difference = this.yaw - lastYaw;
         if ((lastYaw == 512.0 || !(difference > BedAura.this.resetRotate.getValue()) && !(difference < -BedAura.this.resetRotate.getValue()))
            && LemonClient.speedUtil.getPlayerSpeed(player) != 0.0) {
            this.tick = tick;
         } else {
            this.tick = 0;
         }
      }
   }

   class PlaceInfo {
      BedAura.EntityInfo target;
      BlockPos placePos;
      BlockPos basePos;
      float damage;
      float selfDamage;

      public PlaceInfo(BedAura.EntityInfo target, BlockPos placePos, float damage, float selfDamage, BlockPos basePos) {
         this.target = target;
         this.placePos = placePos;
         this.damage = damage;
         this.selfDamage = selfDamage;
         this.basePos = basePos;
      }
   }
}
