package com.lemonclient.client.manager.managers;

import com.lemonclient.api.event.events.PacketEvent;
import com.lemonclient.api.event.events.PlayerJoinEvent;
import com.lemonclient.api.event.events.PlayerLeaveEvent;
import com.lemonclient.api.event.events.Render2DEvent;
import com.lemonclient.api.event.events.Render3DEvent;
import com.lemonclient.api.event.events.RenderEvent;
import com.lemonclient.api.event.events.SendMessageEvent;
import com.lemonclient.api.util.chat.Notification;
import com.lemonclient.api.util.chat.NotificationManager;
import com.lemonclient.api.util.misc.MessageBus;
import com.lemonclient.api.util.player.NameUtil;
import com.lemonclient.api.util.render.RenderUtil;
import com.lemonclient.api.util.world.TimerUtils;
import com.lemonclient.client.LemonClient;
import com.lemonclient.client.PeekCmd;
import com.lemonclient.client.command.CommandManager;
import com.lemonclient.client.manager.Manager;
import com.lemonclient.client.module.Module;
import com.lemonclient.client.module.ModuleManager;
import com.lemonclient.client.module.modules.dev.AntiPush;
import com.lemonclient.client.module.modules.misc.ShulkerBypass;
import com.lemonclient.client.module.modules.qwq.AntiUnicdoe;
import com.lemonclient.mixin.mixins.accessor.AccessorCPacketCustomPayload;
import com.mojang.realmsclient.gui.ChatFormatting;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.Set;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemShulkerBox;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraft.network.play.server.SPacketPlayerListItem.Action;
import net.minecraft.network.play.server.SPacketPlayerListItem.AddPlayerData;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.PlayerSPPushOutOfBlocksEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent.FOVModifier;
import net.minecraftforge.client.event.EntityViewRenderEvent.FogColors;
import net.minecraftforge.client.event.EntityViewRenderEvent.FogDensity;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Post;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Text;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.event.world.WorldEvent.Unload;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.MouseInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public enum ClientEventManager implements Manager {
   INSTANCE;

   final String LAG_MESSAGE = "āȁ́Ё\u0601܁ࠁँਁଁก༁ခᄁሁጁᐁᔁᘁᜁ᠁ᤁᨁᬁᰁᴁḁἁ ℁∁⌁␁━✁⠁⤁⨁⬁Ⰱⴁ⸁⼁、\u3101㈁㌁㐁㔁㘁㜁㠁㤁㨁㬁㰁㴁㸁㼁䀁䄁䈁䌁䐁䔁䘁䜁䠁䤁䨁䬁䰁䴁丁企倁儁刁匁吁唁嘁圁堁夁威嬁封崁币弁态愁戁持搁攁昁朁栁椁樁欁氁洁渁漁瀁焁爁猁琁甁瘁省码礁稁笁簁紁縁缁老脁舁茁萁蔁蘁蜁蠁褁訁謁谁贁踁輁送鄁鈁錁鐁锁阁霁頁餁騁鬁鰁鴁鸁鼁ꀁꄁꈁꌁꐁꔁꘁ꜁ꠁ꤁ꨁꬁ각괁긁꼁뀁넁눁댁됁딁똁뜁렁뤁먁묁밁봁";
   final Set<Character> lagMessageSet = new HashSet<>();
   @EventHandler
   private final Listener<PacketEvent.Receive> receiveListener = new Listener<>(event -> {
      if (event.getPacket() instanceof SPacketPlayerListItem) {
         SPacketPlayerListItem packet = (SPacketPlayerListItem) event.getPacket();
         
         // 1. Xử lý khi người chơi tham gia
         if (packet.getAction() == Action.ADD_PLAYER) {
            for (AddPlayerData playerData : packet.getEntries()) {
               if (playerData.getProfile().getId() != this.getMinecraft().session.getProfile().getId()) {
                  new Thread(() -> {
                     String name = NameUtil.resolveName(playerData.getProfile().getId().toString());
                     if (name != null && this.getPlayer() != null && this.getPlayer().ticksExisted >= 1000) {
                        LemonClient.EVENT_BUS.post(new PlayerJoinEvent(name));
                     }
                  }).start();
               }
            }
         }

         // 2. Xử lý khi người chơi rời đi
         if (packet.getAction() == Action.REMOVE_PLAYER) {
            for (AddPlayerData playerDatax : packet.getEntries()) {
               if (playerDatax.getProfile().getId() != this.getMinecraft().session.getProfile().getId()) {
                  new Thread(() -> {
                     // Sửa lỗi symbol: Sử dụng đúng tên biến playerDatax từ vòng lặp for
                     String name = NameUtil.resolveName(playerDatax.getProfile().getId().toString());
                     if (name != null && this.getPlayer() != null && this.getPlayer().ticksExisted >= 1000) {
                        LemonClient.EVENT_BUS.post(new PlayerLeaveEvent(name));
                     }
                  }).start();
               }
            }
         }
      } // Dấu đóng này kết thúc kiểm tra SPacketPlayerListItem

      // 3. Xử lý cập nhật thời gian từ Server
      if (event.getPacket() instanceof SPacketTimeUpdate) {
         LemonClient.serverUtil.update();
      }
   });

   @SubscribeEvent(priority = EventPriority.LOW)
   public void onRenderGameOverlayEvent(Text event) {
      if (event.getType().equals(ElementType.TEXT)) {
         ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
         Render2DEvent render2DEvent = new Render2DEvent(event.getPartialTicks(), resolution);

         for (Module module : ModuleManager.getModules()) {
            if (module.isEnabled()) {
               this.getProfiler().startSection(module.getName());
               module.onRender2D(render2DEvent);
               this.getProfiler().endSection();
               GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
         }
      }

      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onChatReceived(ClientChatReceivedEvent event) {
      if (this.lagMessageSet.isEmpty()) {
         for (int i = 0;
            i
               < "āȁ́Ё\u0601܁ࠁँਁଁก༁ခᄁሁጁᐁᔁᘁᜁ᠁ᤁᨁᬁᰁᴁḁἁ ℁∁⌁␁━✁⠁⤁⨁⬁Ⰱⴁ⸁⼁、\u3101㈁㌁㐁㔁㘁㜁㠁㤁㨁㬁㰁㴁㸁㼁䀁䄁䈁䌁䐁䔁䘁䜁䠁䤁䨁䬁䰁䴁丁企倁儁刁匁吁唁嘁圁堁夁威嬁封崁币弁态愁戁持搁攁昁朁栁椁樁欁氁洁渁漁瀁焁爁猁琁甁瘁省码礁稁笁簁紁縁缁老脁舁茁萁蔁蘁蜁蠁褁訁謁谁贁踁輁送鄁鈁錁鐁锁阁霁頁餁騁鬁鰁鴁鸁鼁ꀁꄁꈁꌁꐁꔁꘁ꜁ꠁ꤁ꨁꬁ각괁긁꼁뀁넁눁댁됁딁똁뜁렁뤁먁묁밁봁"
                  .length();
            i++
         ) {
            this.lagMessageSet
               .add(
                  "āȁ́Ё\u0601܁ࠁँਁଁก༁ခᄁሁጁᐁᔁᘁᜁ᠁ᤁᨁᬁᰁᴁḁἁ ℁∁⌁␁━✁⠁⤁⨁⬁Ⰱⴁ⸁⼁、\u3101㈁㌁㐁㔁㘁㜁㠁㤁㨁㬁㰁㴁㸁㼁䀁䄁䈁䌁䐁䔁䘁䜁䠁䤁䨁䬁䰁䴁丁企倁儁刁匁吁唁嘁圁堁夁威嬁封崁币弁态愁戁持搁攁昁朁栁椁樁欁氁洁渁漁瀁焁爁猁琁甁瘁省码礁稁笁簁紁縁缁老脁舁茁萁蔁蘁蜁蠁褁訁謁谁贁踁輁送鄁鈁錁鐁锁阁霁頁餁騁鬁鰁鴁鸁鼁ꀁꄁꈁꌁꐁꔁꘁ꜁ꠁ꤁ꨁꬁ각괁긁꼁뀁넁눁댁됁딁똁뜁렁뤁먁묁밁봁"
                     .charAt(i)
               );
         }
      }

      if (!event.getMessage().getFormattedText().contains("{") && !event.getMessage().getFormattedText().contains("}")) {
         if (ModuleManager.isModuleEnabled(AntiUnicdoe.class)) {
            int count = 0;
            String text = event.getMessage().getFormattedText();

            for (int i = 0; i < text.length(); i++) {
               if (this.lagMessageSet.contains(text.charAt(i))) {
                  count++;
               }
            }

            if (count >= 25) {
               event.setCanceled(true);
               TextComponentString string = new TextComponentString("(lag message)");
               Minecraft.getMinecraft().player.sendMessage(string);
               return;
            }
         }

         LemonClient.EVENT_BUS.post(event);
      } else {
         event.setCanceled(true);
         TextComponentString string = new TextComponentString(
            event.getMessage().getFormattedText().replace("{", "").replace("}", "").replace("$", "").replace("ldap", "")
         );
         Minecraft.getMinecraft().player.sendMessage(string);
      }
   }

   @SubscribeEvent
   public void onAttackEntity(AttackEntityEvent event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onRenderBlockOverlay(RenderBlockOverlayEvent event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onLivingEntityUseItemFinish(Finish event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onInputUpdate(InputUpdateEvent event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onLivingDeath(LivingDeathEvent event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onPlayerPush(PlayerSPPushOutOfBlocksEvent event) {
      if (ModuleManager.isModuleEnabled(AntiPush.class)) {
         event.setCanceled(true);
      }

      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onEntitySpawn(EntityJoinWorldEvent event) {
      Entity entity = event.getEntity();
      if (entity instanceof EntityItem) {
         PeekCmd.drop = (EntityItem)entity;
         PeekCmd.metadataTicks = 0;
      }
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onWorldUnload(Unload event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onGuiOpen(GuiOpenEvent event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onFogColor(FogColors event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onFogDensity(FogDensity event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onFov(FOVModifier event) {
      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onTick(ClientTickEvent event) {
      if (ModuleManager.isModuleEnabled("Peek") && ShulkerBypass.shulkers) {
         if (event.phase == Phase.END) {
            if (PeekCmd.guiTicks > -1) {
               PeekCmd.guiTicks++;
            }

            if (PeekCmd.metadataTicks > -1) {
               PeekCmd.metadataTicks++;
            }
         }

         if (PeekCmd.metadataTicks >= ShulkerBypass.delay) {
            PeekCmd.metadataTicks = -1;
            if (PeekCmd.drop.getItem().getItem() instanceof ItemShulkerBox) {
               MessageBus.sendClientDeleteMessage(
                  "New shulker found. use /peek to view its content " + TextFormatting.GREEN + "(" + PeekCmd.drop.getItem().getDisplayName() + ")",
                  Notification.Type.INFO,
                  "Peek",
                  3
               );
               PeekCmd.shulker = PeekCmd.drop.getItem();
            }
         }

         if (PeekCmd.guiTicks == 20) {
            PeekCmd.guiTicks = -1;
            Minecraft.getMinecraft().player.displayGUIChest(PeekCmd.toOpen);
         }
      }

      if (this.getMinecraft().player != null && this.getMinecraft().world != null) {
         int timerSpeed = (int)TimerUtils.getTimer();

         for (Module module : ModuleManager.getModules()) {
            try {
               if (module.isEnabled()) {
                  module.onTickTimer++;
                  if (module.onTickTimer >= timerSpeed) {
                     module.onTick();
                     module.onTickTimer = 0;
                  }
               }
            } catch (Exception var10) {
               if (this.getWorld() != null && this.getPlayer() != null) {
                  MessageBus.sendClientPrefixMessage("Disabled " + module.getName() + " due to " + var10, Notification.Type.ERROR);
               }

               module.setEnabled(false);

               for (StackTraceElement stack : var10.getStackTrace()) {
                  System.out.println(stack.toString());
               }
            }
         }
      }

      LemonClient.EVENT_BUS.post(event);
   }

   @SubscribeEvent
   public void onUpdate(LivingUpdateEvent event) {
      if (this.getMinecraft().player != null && this.getMinecraft().world != null) {
         if (event.getEntity().getEntityWorld().isRemote && event.getEntityLiving() == this.getPlayer()) {
            int timerSpeed = (int)TimerUtils.getTimer();

            for (Module module : ModuleManager.getModules()) {
               try {
                  if (module.isEnabled()) {
                     module.onUpdateTimer++;
                     if (module.onUpdateTimer >= timerSpeed) {
                        module.onUpdate();
                        module.onUpdateTimer = 0;
                     }
                  }
               } catch (Exception var10) {
                  if (this.getWorld() != null && this.getPlayer() != null) {
                     MessageBus.sendClientPrefixMessage("Disabled " + module.getName() + " due to " + var10, Notification.Type.ERROR);
                  }

                  module.setEnabled(false);

                  for (StackTraceElement stack : var10.getStackTrace()) {
                     System.out.println(stack.toString());
                  }
               }
            }

            LemonClient.EVENT_BUS.post(event);
         }
      }
   }

   @SubscribeEvent
   public void onWorldRender(RenderWorldLastEvent event) {
      if (!event.isCanceled()) {
         if (this.getMinecraft().player != null && this.getMinecraft().world != null) {
            this.getProfiler().startSection("lemonclient");
            this.getProfiler().startSection("setup");
            RenderUtil.prepare();
            RenderEvent event1 = new RenderEvent(event.getPartialTicks());
            this.getProfiler().endSection();

            for (Module module : ModuleManager.getModules()) {
               if (module.isEnabled()) {
                  this.getProfiler().startSection(module.getName());
                  module.onWorldRender(event1);
                  this.getProfiler().endSection();
               }
            }

            this.getProfiler().startSection("release");
            RenderUtil.release();
            this.getProfiler().endSection();
            this.getProfiler().endSection();
         }
      }
   }

   @SubscribeEvent
   public void onRender3D(RenderWorldLastEvent event) {
      if (!event.isCanceled()) {
         if (this.getMinecraft().player != null && this.getMinecraft().world != null) {
            this.getProfiler().startSection("lemonclient");
            this.getProfiler().startSection("setup");
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.shadeModel(7425);
            GlStateManager.disableDepth();
            GlStateManager.glLineWidth(1.0F);
            Render3DEvent event2 = new Render3DEvent(event.getPartialTicks());
            this.getProfiler().endSection();

            for (Module module : ModuleManager.getModules()) {
               if (module.isEnabled()) {
                  this.getProfiler().startSection(module.getName());
                  module.onRender3D(event2);
                  this.getProfiler().endSection();
               }
            }

            this.getProfiler().startSection("release");
            GlStateManager.glLineWidth(1.0F);
            GlStateManager.shadeModel(7424);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.enableCull();
            GlStateManager.enableCull();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.enableDepth();
            this.getProfiler().endSection();
            this.getProfiler().endSection();
         }
      }
   }

   @SubscribeEvent
   public void onRender(Post event) {
      if (this.getMinecraft().player != null && this.getMinecraft().world != null) {
         if (event.getType() == ElementType.HOTBAR) {
            for (Module module : ModuleManager.getModules()) {
               if (module.isEnabled()) {
                  module.onRender();
                  NotificationManager.draw();
               }
            }

            LemonClient.INSTANCE.gameSenseGUI.render();
         }

         LemonClient.EVENT_BUS.post(event);
      }
   }

   @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
   public void onKeyInput(KeyInputEvent event) {
      if (Keyboard.getEventKeyState() && Keyboard.getEventKey() != 0) {
         EntityPlayerSP player = this.getPlayer();
         if (player != null && !player.isSneaking()) {
            String prefix = CommandManager.getCommandPrefix();
            char typedChar = Keyboard.getEventCharacter();
            if (prefix.length() == 1 && prefix.charAt(0) == typedChar) {
               this.getMinecraft().displayGuiScreen(new GuiChat(prefix));
            }
         }

         int key = Keyboard.getEventKey();
         if (key != 0) {
            for (Module module : ModuleManager.getModules()) {
               if (module.getBind() == key) {
                  module.toggle();
               }
            }
         }

         LemonClient.INSTANCE.gameSenseGUI.handleKeyEvent(Keyboard.getEventKey());
      }
   }

   @SubscribeEvent
   public void onMouseInput(MouseInputEvent event) {
      if (Mouse.getEventButtonState()) {
         LemonClient.EVENT_BUS.post(event);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public void onChatSent(ClientChatEvent event) {
      if (event.getMessage().startsWith(CommandManager.getCommandPrefix())) {
         event.setCanceled(true);

         try {
            this.getMinecraft().ingameGUI.getChatGUI().addToSentMessages(event.getMessage());
            CommandManager.callCommand(event.getMessage().substring(1), false);
         } catch (Exception var3) {
            var3.printStackTrace();
            MessageBus.sendCommandMessage(ChatFormatting.DARK_RED + "Error: " + var3.getMessage(), true);
         }
      } else {
         SendMessageEvent eventNow = new SendMessageEvent(event.getMessage());
         LemonClient.EVENT_BUS.post(eventNow);
         if (eventNow.isCancelled()) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public void init(ClientTickEvent event) {
      this.fastest();
   }

   @SubscribeEvent
   public void init(ServerTickEvent event) {
      this.fastest();
   }

   @SubscribeEvent
   public void init(PlayerTickEvent event) {
      this.fastest();
   }

   @SubscribeEvent
   public void init(WorldTickEvent event) {
      this.fastest();
   }

   public void fastest() {
      if (this.getMinecraft().player != null && this.getMinecraft().world != null) {
         int timerSpeed = (int)TimerUtils.getTimer();

         for (Module module : ModuleManager.getModules()) {
            try {
               if (module.isEnabled()) {
                  module.fastTimer++;
                  if (module.fastTimer >= timerSpeed) {
                     module.fast();
                     module.fastTimer = 0;
                  }
               }
            } catch (Exception var9) {
               if (this.getWorld() != null && this.getPlayer() != null) {
                  MessageBus.sendClientPrefixMessage("Disabled " + module.getName() + " due to " + var9, Notification.Type.ERROR);
               }

               module.setEnabled(false);

               for (StackTraceElement stack : var9.getStackTrace()) {
                  System.out.println(stack.toString());
               }
            }
         }
      }
   }
}
