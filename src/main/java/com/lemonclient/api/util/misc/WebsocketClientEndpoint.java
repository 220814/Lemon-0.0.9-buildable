package com.lemonclient.api.util.misc;

import java.io.IOException;
import java.net.URI;
// Thay đổi từ shaded.websocket sang javax.websocket
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

@ClientEndpoint
public class WebsocketClientEndpoint {
   Session userSession = null;
   private WebsocketClientEndpoint.MessageHandler messageHandler;

   public int getUserSession() {
      return this.userSession == null ? 0 : 1;
   }

   public void close() {
      try {
         if (this.userSession != null) {
            this.userSession.close();
         }
      } catch (NullPointerException | IOException var2) {
      }
   }

   public WebsocketClientEndpoint(URI endpointURI) {
      try {
         // Đảm bảo ClassLoader có thể tìm thấy thư viện WebSocket trong môi trường Mod Minecraft
         Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
         WebSocketContainer container = ContainerProvider.getWebSocketContainer();
         this.userSession = container.connectToServer(this, endpointURI);
      } catch (Exception var3) {
         var3.printStackTrace(); // Thêm dòng này để dễ theo dõi nếu kết nối lỗi
      }
   }

   @OnOpen
   public void onOpen(Session userSession) {
      System.out.println("opening websocket");
      this.userSession = userSession;
   }

   @OnClose
   public void onClose(Session userSession, CloseReason reason) {
      System.out.println("closing websocket");
      this.userSession = null;
   }

   @OnMessage
   public void onMessage(String message) {
      if (this.messageHandler != null) {
         this.messageHandler.handleMessage(message);
      }
   }

   public void addMessageHandler(WebsocketClientEndpoint.MessageHandler msgHandler) {
      this.messageHandler = msgHandler;
   }

   public void sendMessage(String message) {
      if (this.userSession != null) {
         RemoteEndpoint.Async remoteEndpoint = this.userSession.getAsyncRemote();
         remoteEndpoint.sendText(message);
      }
   }

   public interface MessageHandler {
      void handleMessage(String var1);
   }
}
