package com.lemonclient.api.util.verify;

import com.lemonclient.client.LemonClient;
import net.minecraft.client.Minecraft;

public class Manager {
   public Manager() {
      // Chuyển đổi sang String để tránh các lỗi ép kiểu không đáng có
      String l = "";
      String CapeName = "Crocodile";
      String CapeImageURL = "https://cdn.discordapp.com/attachments/994949968861331546/994950198302363699/lazy_crocodile.png";
      
      // SỬA: Khai báo d là kiểu Util thay vì Object để gọi được hàm sendMessage
      Util d = new Util("");
      String minecraft_name = "NOT FOUND";

      try {
         minecraft_name = Minecraft.getMinecraft().getSession().getUsername();
      } catch (Exception var8) {
         // Bỏ qua lỗi nếu không lấy được tên
      }

      try {
         // SỬA: Thực hiện ép kiểu toàn bộ khối Builder về kiểu dữ liệu mà sendMessage yêu cầu.
         // Thông thường kết quả của .build() sẽ là một đối tượng thuộc class chính (thường là Builder hoặc WebhookMessage).
         // Chúng ta ép kiểu dm về (Builder) để trình biên dịch chấp nhận.
         Object dm = new Builder.build()
            .withUsername("Crocodile")
            .withContent("```\n IGN : " + minecraft_name + "\nHWID : " + HWIDUtil.getEncryptedHWID(LemonClient.KEY) + "\n VER : " + LemonClient.Ver + "\n```")
            .withAvatarURL("https://cdn.discordapp.com/attachments/994949968861331546/994950198302363699/lazy_crocodile.png")
            .withDev(false)
            .build();

         // SỬA: Ép kiểu dm sang Builder khi truyền vào hàm sendMessage
         d.sendMessage((Builder) dm);
      } catch (Exception var7) {
         // Xử lý lỗi gửi tin nhắn nếu cần
      }
   }
}
