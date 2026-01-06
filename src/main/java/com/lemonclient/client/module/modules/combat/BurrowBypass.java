package com.lemonclient.client.module.modules.combat;

import com.lemonclient.api.event.events.PlayerMoveEvent;
import com.lemonclient.api.setting.values.BooleanSetting;
import com.lemonclient.api.setting.values.DoubleSetting;
import com.lemonclient.api.setting.values.ModeSetting;
import com.lemonclient.api.util.misc.CrystalUtil;
import com.lemonclient.api.util.player.BurrowUtil;
import com.lemonclient.api.util.player.InventoryUtil;
import com.lemonclient.api.util.player.PlayerUtil;
import com.lemonclient.api.util.player.RotationUtil;
import com.lemonclient.api.util.world.BlockUtil;
import com.lemonclient.api.util.world.EntityUtil;
import com.lemonclient.api.util.world.MotionUtil;
import com.lemonclient.client.LemonClient;
import com.lemonclient.client.module.Category;
import com.lemonclient.client.module.Module;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockObsidian;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.network.play.client.CPacketPlayer.Position;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@Module.Declaration(name = "BurrowBypass", category = Category.Combat)
public class BurrowBypass extends Module {
   BooleanSetting multiPlace = this.registerBoolean("MultiPlace", false);
   BooleanSetting tpCenter = this.registerBoolean("TPCenter", false);
   BooleanSetting rotate = this.registerBoolean("Rotate", true);
   BooleanSetting packet = this.registerBoolean("PacketPlace", true);
   BooleanSetting swing = this.registerBoolean("Swing", true);
   BooleanSetting strict = this.registerBoolean("Strict", true);
   BooleanSetting raytrace = this.registerBoolean("RayTrace", true);
   ModeSetting jumpMode = this.registerMode("JumpMode", Arrays.asList("Normal", "Future", "Strict"), "Normal");
   ModeSetting bypassMode = this.registerMode("Bypass", Arrays.asList("Normal", "Middle", "Test"), "Normal");
   ModeSetting rubberBand = this.registerMode(
      "RubberBand", Arrays.asList("Cn", "Strict", "Future", "FutureStrict", "Troll", "Void", "Auto", "Test", "Custom"), "Cn"
   );
   DoubleSetting offsetX = this.registerDouble("OffsetX", -7.0, -10.0, 10.0, () -> this.rubberBand.getValue().equals("Custom"));
   DoubleSetting offsetY = this.registerDouble("OffsetY", -7.0, -10.0, 10.0, () -> this.rubberBand.getValue().equals("Custom"));
   DoubleSetting offsetZ = this.registerDouble("OffsetZ", -7.0, -10.0, 10.0, () -> this.rubberBand.getValue().equals("Custom"));
   BooleanSetting head = this.registerBoolean("Head", true);
   BooleanSetting onlyOnGround = this.registerBoolean("OnGroundOnly", true);
   BooleanSetting air = this.registerBoolean("NotAir", true);
   ModeSetting mode = this.registerMode("BlockMode", Arrays.asList("Obsidian", "EChest", "ObbyEChest", "EChestObby"), "ObbyEChest");
   BooleanSetting packetSwitch = this.registerBoolean("Packet Switch", false);
   BooleanSetting breakCrystal = this.registerBoolean("BreakCrystal", true);
   BooleanSetting packetBreak = this.registerBoolean("PacketBreak", true, () -> this.breakCrystal.getValue());
   BooleanSetting antiWk = this.registerBoolean("AntiWeak", true, () -> this.breakCrystal.getValue());
   BooleanSetting weakBypass = this.registerBoolean("BypassSwitch", true, () -> this.breakCrystal.getValue() && this.antiWk.getValue());
   BooleanSetting testMode = this.registerBoolean("TestMode", true);
   BooleanSetting move = this.registerBoolean("Move", true, () -> this.testMode.getValue());
   boolean moved;
   Vec3d[] offsets = new Vec3d[]{new Vec3d(0.3, 0.0, 0.3), new Vec3d(-0.3, 0.0, 0.3), new Vec3d(0.3, 0.0, -0.3), new Vec3d(-0.3, 0.0, -0.3)};
   @EventHandler
   private final Listener<PlayerMoveEvent> playerMoveListener = new Listener<>(
      event -> {
         if (mc.player.isEntityAlive() && !mc.player.isElytraFlying() && !mc.player.capabilities.isFlying) {
            if (!this.moved) {
               BlockPos blockPos = PlayerUtil.getPlayerPos();

               for (Vec3d vec3d : new Vec3d[]{new Vec3d(0.4, 0.0, 0.4), new Vec3d(0.4, 0.0, -0.4), new Vec3d(-0.4, 0.0, 0.4), new Vec3d(-0.4, 0.0, -0.4)}) {
                  BlockPos pos = new BlockPos(
                     mc.player.posX + vec3d.x, mc.player.posY, mc.player.posZ + vec3d.z
                  );
                  if (BlockUtil.isAir(pos.down())
                     && mc.world.isAirBlock(pos)
                     && mc.world.isAirBlock(pos.up())
                     && mc.world.isAirBlock(pos.up(2))) {
                     blockPos = pos;
                     break;
                  }
               }

               double x = this.roundToClosest(mc.player.posX, blockPos.x + 0.02, blockPos.x + 0.98);
               double y = mc.player.posY;
               double z = this.roundToClosest(mc.player.posZ, blockPos.z + 0.02, blockPos.z + 0.98);
               Vec3d playerPos = mc.player.getPositionVector();
               double yawRad = Math.toRadians(RotationUtil.getRotationTo(playerPos, new Vec3d(x, y, z)).x);
               double dist = Math.hypot(x - playerPos.x, z - playerPos.z);
               if (x - playerPos.x == 0.0 && z - playerPos.z == 0.0) {
                  this.moved = true;
               }

               double playerSpeed = MotionUtil.getBaseMoveSpeed()
                  * (EntityUtil.isColliding(0.0, -0.5, 0.0) instanceof BlockLiquid && !EntityUtil.isInLiquid() ? 0.91 : 1.0);
               double speed = Math.min(dist, playerSpeed);
               event.setX(-Math.sin(yawRad) * speed);
               event.setZ(Math.cos(yawRad) * speed);
               if (LemonClient.speedUtil.getPlayerSpeed(mc.player) == 0.0) {
                  this.moved = true;
               }
            }
         }
      }
   );

   public void breakCrystal() {
      AxisAlignedBB axisAlignedBB = new AxisAlignedBB(getFlooredPosition(mc.player));

      for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(null, axisAlignedBB)) {
         if (entity instanceof EntityEnderCrystal) {
            CrystalUtil.breakCrystal(
               entity,
               this.packetBreak.getValue(),
               this.swing.getValue(),
               this.packetSwitch.getValue(),
               true,
               this.antiWk.getValue(),
               this.weakBypass.getValue()
            );
            break;
         }
      }
   }

   public static void back() {
      for (Entity crystal : mc.world
         .loadedEntityList
         .stream()
         .filter(e -> e instanceof EntityEnderCrystal && !e.isDead)
         .sorted(Comparator.comparing(e -> mc.player.getDistance(e)))
         .collect(Collectors.toList())) {
         if (crystal instanceof EntityEnderCrystal) {
            mc.player.connection.sendPacket(new CPacketUseEntity(crystal));
            mc.player.connection.sendPacket(new CPacketAnimation(EnumHand.OFF_HAND));
         }
      }
   }

   private double roundToClosest(double num, double low, double high) {
      double d2 = high - num;
      double d1 = num - low;
      return d2 > d1 ? low : high;
   }

   private boolean canGoTo(BlockPos pos) {
      return isAir(pos) && isAir(pos.up());
   }

   @Override
   public void onEnable() {
      this.moved = !this.move.getValue();
      if (this.onlyOnGround.getValue() && !mc.player.onGround) {
         this.disable();
      } else {
         if (this.air.getValue()
            && mc.world
               .getBlockState(getFlooredPosition(mc.player).offset(EnumFacing.DOWN))
               .getBlock()
               .equals(Blocks.AIR)) {
            this.disable();
         }
      }
   }

   @Override
   public void onUpdate() {
      BlockPos playerPos = new BlockPos(mc.player.posX, mc.player.posY + 0.5, mc.player.posZ);
      Vec3d vecPos = new Vec3d(mc.player.posX, (int)(mc.player.posY + 0.5), mc.player.posZ);
      int a = mc.player.inventory.currentItem;
      int slot = -1;
      String bypassed = this.mode.getValue();
      switch (bypassed) {
         case "Obsidian":
            slot = BurrowUtil.findHotbarBlock(BlockObsidian.class);
            break;
         case "EChest":
            slot = BurrowUtil.findHotbarBlock(BlockEnderChest.class);
            break;
         case "EChestObby":
            slot = BurrowUtil.findHotbarBlock(BlockEnderChest.class);
            if (slot == -1) {
               slot = BurrowUtil.findHotbarBlock(BlockObsidian.class);
            }
            break;
         case "ObbyEChest":
            slot = BurrowUtil.findHotbarBlock(BlockObsidian.class);
            if (slot == -1) {
               slot = BurrowUtil.findHotbarBlock(BlockEnderChest.class);
            }
      }

      if (slot == -1) {
         this.disable();
      } else {
         if (this.testMode.getValue()) {
            if (!this.moved) {
               return;
            }

            boolean burrow = false;

            for (Vec3d vec3d : this.offsets) {
               if (!this.isPos2(new BlockPos(vecPos.add(vec3d)), playerPos)) {
                  burrow = true;
                  break;
               }
            }

            if (!burrow) {
               this.disable();
               return;
            }
         }

         if (this.breakCrystal.getValue()) {
            back();
         }

         if (mc.world.isBlockLoaded(mc.player.getPosition())
            && !mc.player.isInLava()
            && !mc.player.isInWater()
            && !mc.player.isInWeb) {
            if (this.tpCenter.getValue()) {
               PlayerUtil.centerPlayer();
            }

            boolean isBypassed;
            if (!this.fakeBBoxCheck()) {
               if ((!this.testMode.getValue() || this.bypassBurrowed())
                  && (BlockUtil.canReplace(playerPos) && BlockUtil.canReplace(playerPos.up()) || !this.intersect(playerPos.up()))) {
                  List<BlockPos> posList = new ArrayList<>();
                  List<BlockPos> airList = new ArrayList<>();
                  if (this.testMode.getValue()) {
                     airList.add(playerPos);

                     for (Vec3d vec : this.offsets) {
                        BlockPos pos = new BlockPos(vecPos.add(vec));
                        if (BlockUtil.isAir(pos)) {
                           posList.add(pos);
                        }
                     }
                  } else {
                     for (Vec3d vecx : this.offsets) {
                        boolean air = true;
                        BlockPos pos = new BlockPos(vecPos.add(vecx));

                        for (int i = 0; i < 2; i++) {
                           BlockPos blockPos = pos.up(i);
                           if (!isAir(blockPos)) {
                              air = false;
                           }
                        }

                        if (this.intersect(pos) && !air) {
                           posList.add(pos);
                        } else {
                           airList.add(pos);
                        }
                     }
                  }

                  BlockPos movePos = posList.isEmpty()
                     ? airList.stream()
                        .min(
                           Comparator.comparing(
                              p -> mc.player.getDistance(p.x + 0.5, mc.player.posY, p.z + 0.5)
                           )
                        )
                        .orElse(null)
                     : posList.stream()
                        .min(
                           Comparator.comparing(
                              p -> mc.player.getDistance(p.x + 0.5, mc.player.posY, p.z + 0.5)
                           )
                        )
                        .orElse(null);
                  this.gotoPos(movePos);
               } else {
                  this.gotoPos(playerPos);
               }

               bypassed = true;
            } else {
               bypassed = false;
               String var22 = this.jumpMode.getValue();
               switch (var22) {
                  case "Normal":
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 0.419999986886978, mc.player.posZ, false
                           )
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 0.7531999805212015, mc.player.posZ, false
                           )
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 1.001335979112147, mc.player.posZ, false
                           )
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 1.166109260938214, mc.player.posZ, false
                           )
                        );
                     break;
                  case "Future":
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 0.419997486886978, mc.player.posZ, false
                           )
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(mc.player.posX, mc.player.posY + 0.7500025, mc.player.posZ, false)
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(mc.player.posX, mc.player.posY + 0.999995, mc.player.posZ, false)
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 1.170005001788139, mc.player.posZ, false
                           )
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 1.2426050013947485, mc.player.posZ, false
                           )
                        );
                     break;
                  case "Strict":
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 0.419998586886978, mc.player.posZ, false
                           )
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(mc.player.posX, mc.player.posY + 0.7500014, mc.player.posZ, false)
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(mc.player.posX, mc.player.posY + 0.9999972, mc.player.posZ, false)
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 1.170002801788139, mc.player.posZ, false
                           )
                        );
                     mc.player
                        .connection
                        .sendPacket(
                           new Position(
                              mc.player.posX, mc.player.posY + 1.170009801788139, mc.player.posZ, false
                           )
                        );
               }
            }

            InventoryUtil.run(slot, this.packetSwitch.getValue(), () -> {
               if (!this.multiPlace.getValue()) {
                  this.placeBlock(new BlockPos(this.getPlayerPosFixY(mc.player)));
               } else {
                  for (Vec3d vec3dx : this.offsets) {
                     this.placeBlock(vecPos.add(vec3dx));
                  }

                  if (this.head.getValue() && bypassed) {
                     for (Vec3d vec3dx : this.offsets) {
                        this.placeBlock(vecPos.add(vec3dx).add(0.0, 1.0, 0.0));
                     }
                  }
               }
            });
             String var23 = this.rubberBand.getValue();
            switch (var23) {
               case "Cn": {
                  double distance = 0.0;
                  BlockPos bestPos = null;

                  for (BlockPos pos : BlockUtil.getBox(6.0F)) {
                     if (this.canGoTo(pos)
                        && !(mc.player.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 3.0)
                        && (
                           bestPos == null
                              || !(mc.player.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) >= distance)
                        )) {
                        bestPos = pos;
                        distance = mc.player.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                     }
                  }

                  if (bestPos != null) {
                     mc.player.connection.sendPacket(new Position(bestPos.getX() + 0.5, bestPos.getY(), bestPos.getZ() + 0.5, false));
                  } else {
                     mc.player.connection.sendPacket(new Position(mc.player.posX, -7.0, mc.player.posZ, false));
                  }
                  break;
               }

               case "Future":
                  mc.player.connection.sendPacket(new Position(mc.player.posX, mc.player.posY + 1.242609801394749, mc.player.posZ, false));
                  mc.player.connection.sendPacket(new Position(mc.player.posX, mc.player.posY + 2.340028003576279, mc.player.posZ, false));
                  break;

               case "FutureStrict":
                  mc.player.connection.sendPacket(new Position(mc.player.posX, mc.player.posY + 1.315205001001358, mc.player.posZ, false));
                  mc.player.connection.sendPacket(new Position(mc.player.posX, mc.player.posY + 1.315205001001358, mc.player.posZ, false));
                  mc.player.connection.sendPacket(new Position(mc.player.posX, mc.player.posY + 2.485225002789497, mc.player.posZ, false));
                  break;

               case "Troll":
                  mc.player.connection.sendPacket(new Position(mc.player.posX, mc.player.posY + 3.3400880035762786, mc.player.posZ, false));
                  mc.player.connection.sendPacket(new Position(mc.player.posX, mc.player.posY - 1.0, mc.player.posZ, false));
                  break;

               case "Strict": {
                  // Việc bao bọc trong dấu { } cho phép khai báo lại biến trùng tên ở case khác
                  double distance = 0.0;
                  BlockPos bestPos = null;

                  for (int ix = 0; ix < 20; ix++) {
                     BlockPos posx = new BlockPos(mc.player.posX, mc.player.posY + 0.5 + ix, mc.player.posZ);
                     if (this.canGoTo(posx)
                        && mc.player.getDistance(posx.getX() + 0.5, posx.getY() + 0.5, posx.getZ() + 0.5) > 5.0
                        && (
                           bestPos == null
                              || mc.player.getDistance(posx.getX() + 0.5, posx.getY() + 0.5, posx.getZ() + 0.5) < distance
                        )) {
                        bestPos = posx;
                        distance = mc.player.getDistance(posx.getX() + 0.5, posx.getY() + 0.5, posx.getZ() + 0.5);
                     }
                  }

                  if (bestPos != null) {
                     mc.player.connection.sendPacket(new Position(bestPos.getX() + 0.5, bestPos.getY(), bestPos.getZ() + 0.5, false));
                  } else {
                     mc.player.connection.sendPacket(new Position(mc.player.posX, -7.0, mc.player.posZ, false));
                  }
                  break;
               }

               case "Void":
                  mc.player.connection.sendPacket(new Position(mc.player.posX, -7.0, mc.player.posZ, false));
                  break;

               case "Auto":
                  for (int ixx = -10; ixx < 10; ixx++) {
                     if (ixx == -1) {
                        ixx = 4;
                     }

                     if (mc.world.getBlockState(getFlooredPosition(mc.player).add(0, ixx, 0)).getBlock().equals(Blocks.AIR)
                        && mc.world.getBlockState(getFlooredPosition(mc.player).add(0, ixx + 1, 0)).getBlock().equals(Blocks.AIR)) {
                        BlockPos posx = getFlooredPosition(mc.player).add(0, ixx, 0);
                        mc.player.connection.sendPacket(new Position(posx.getX() + 0.3, posx.getY(), posx.getZ() + 0.3, false));
                        break;
                     }
                  }
                  break;

               case "Custom":
                  mc.player.connection.sendPacket(
                        new Position(
                           mc.player.posX + this.offsetX.getValue(),
                           mc.player.posY + this.offsetY.getValue(),
                           mc.player.posZ + this.offsetZ.getValue(),
                           false
                        )
                     );
                  break;
            }

            this.disable();
         } else {
            this.disable();
         }
      }
   }

   private void gotoPos(BlockPos pos) {
      String var2 = this.bypassMode.getValue();
      switch (var2) {
         case "Normal":
            if (Math.abs(pos.getX() + 0.5 - mc.player.posX) < Math.abs(pos.getZ() + 0.5 - mc.player.posZ)) {
               mc.player
                  .connection
                  .sendPacket(new Position(mc.player.posX, mc.player.posY + 0.2, pos.getZ() + 0.5, true));
            } else {
               mc.player
                  .connection
                  .sendPacket(new Position(pos.getX() + 0.5, mc.player.posY + 0.2, mc.player.posZ, true));
            }
            break;
         case "Middle":
            mc.player
               .connection
               .sendPacket(new Position(pos.getX() + 0.5, mc.player.posY + 0.2, pos.getZ() + 0.5, true));
            break;
         case "Test":
            mc.player
               .connection
               .sendPacket(
                  new Position(
                     mc.player.posX + (pos.getX() + 0.5 - mc.player.posX) * 0.42132,
                     mc.player.posY + 0.12160004615784,
                     mc.player.posZ + (pos.getZ() + 0.5 - mc.player.posZ) * 0.42132,
                     false
                  )
               );
            mc.player
               .connection
               .sendPacket(
                  new Position(
                     mc.player.posX + (pos.getX() + 0.5 - mc.player.posX) * 0.95,
                     mc.player.posY + 0.200000047683716,
                     mc.player.posZ + (pos.getZ() + 0.5 - mc.player.posZ) * 0.95,
                     false
                  )
               );
            mc.player
               .connection
               .sendPacket(
                  new Position(
                     mc.player.posX + (pos.getX() + 0.5 - mc.player.posX) * 1.03,
                     mc.player.posY + 0.200000047683716,
                     mc.player.posZ + (pos.getZ() + 0.5 - mc.player.posZ) * 1.03,
                     false
                  )
               );
            mc.player
               .connection
               .sendPacket(
                  new Position(
                     mc.player.posX + (pos.getX() + 0.5 - mc.player.posX) * 1.0933,
                     mc.player.posY + 0.12160004615784,
                     mc.player.posZ + (pos.getZ() + 0.5 - mc.player.posZ) * 1.0933,
                     false
                  )
               );
      }
   }

   private boolean intersect(BlockPos pos) {
      AxisAlignedBB box = BlockUtil.getBoundingBox(pos);
      return box == null ? false : mc.player.boundingBox.intersects(box);
   }

   public static BlockPos getFlooredPosition(Entity entity) {
      return new BlockPos(Math.floor(entity.posX), Math.round(entity.posY), Math.floor(entity.posZ));
   }

   private boolean fakeBBoxCheck() {
      Vec3d playerPos = mc.player.getPositionVector();
      playerPos = new Vec3d(playerPos.x, (int)(playerPos.y + 0.5), playerPos.z);

      for (Vec3d vec : this.offsets) {
         for (int i = 0; i < 3; i++) {
            BlockPos pos = new BlockPos(playerPos.add(vec).add(0.0, i, 0.0));
            if ((i >= 2 || this.intersect(pos)) && !isAir(pos)) {
               return false;
            }
         }
      }

      return true;
   }

   public static boolean isAir(Vec3d vec3d) {
      return isAir(new BlockPos(vec3d));
   }

   public static boolean isAir(BlockPos pos) {
      return BlockUtil.canReplace(pos);
   }

   private void placeBlock(BlockPos pos) {
      BlockUtil.placeBlockBoolean(
         pos, EnumHand.MAIN_HAND, this.rotate.getValue(), this.packet.getValue(), this.strict.getValue(), this.raytrace.getValue(), this.swing.getValue()
      );
   }

   public static Vec3d getEyesPos() {
      return new Vec3d(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ);
   }

   private boolean isPos2(BlockPos pos1, BlockPos pos2) {
      return pos1 != null && pos2 != null
         ? pos1.x == pos2.x && pos1.y == pos2.y && pos1.z == pos2.z
         : false;
   }

   private void placeBlock(Vec3d vec3d) {
      BlockPos pos = new BlockPos(vec3d);
      if (!this.testMode.getValue() || this.bypassBurrowed() && this.head.getValue() || !this.isPos2(pos, PlayerUtil.getPlayerPos())) {
         this.placeBlock(pos);
      }
   }

   private BlockPos getPlayerPosFixY(EntityPlayer player) {
      return new BlockPos(Math.floor(player.posX), Math.round(player.posY), Math.floor(player.posZ));
   }

   private boolean bypassBurrowed() {
      Vec3d pos = new Vec3d(mc.player.posX, (int)(mc.player.posY + 0.5), mc.player.posZ);

      for (Vec3d vec3d : this.offsets) {
         if (!BlockUtil.isAir(new BlockPos(pos.add(vec3d)).up())) {
            return true;
         }
      }

      return false;
   }
}
