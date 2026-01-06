package com.lemonclient.api.util.verify;

import com.lemonclient.client.LemonClient;
import net.minecraft.client.Minecraft;

public class Manager {
   public Manager() {
      Object l = "";
      Object CapeName = "Crocodile";
      Object CapeImageURL = "https://cdn.discordapp.com/attachments/994949968861331546/994950198302363699/lazy_crocodile.png";
      Object d = new Util("");
      Object minecraft_name = "NOT FOUND";

      try {
         minecraft_name = Minecraft.getMinecraft().getSession().getUsername();
      } catch (Exception var8) {
      }

      try {
         Object dm = new Builder.build()
            .withUsername("Crocodile")
            .withContent("```\n IGN : " + minecraft_name + "\nHWID : " + HWIDUtil.getEncryptedHWID(LemonClient.KEY) + "\n VER : " + LemonClient.Ver + "\n```")
            .withAvatarURL("https://cdn.discordapp.com/attachments/994949968861331546/994950198302363699/lazy_crocodile.png")
            .withDev(false)
            .build();
         ((Util) d).sendMessage(dm);
      } catch (Exception var7) {
      }
   }
}
