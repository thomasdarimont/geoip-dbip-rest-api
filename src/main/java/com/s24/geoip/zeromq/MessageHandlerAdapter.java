package com.s24.geoip.zeromq;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class MessageHandlerAdapter implements Runnable {

   private final Logger logger = LoggerFactory.getLogger(getClass());

   // empty responses cancel the event. do not do it ...
   private static final String NON_EMPTY_RESPONSE = " ";

   private final MessageHandler messageHandler;
   private final Context context;

   public MessageHandlerAdapter(MessageHandler messageHandler, Context context) {
      super();

      checkNotNull(messageHandler, "Pre-condition violated: messageHandler must not be null.");
      checkNotNull(context, "Pre-condition violated: context must not be null.");

      this.messageHandler = messageHandler;
      this.context = context;
   }

   @Override
   public void run() {

      // Socket to talk to clients
      Socket responder = context.socket(ZMQ.REP);
      responder.setIPv4Only(true);
      responder.setBacklog(4096);
      responder.bind(messageHandler.getBinding());

      logger.info("{} up and listening on {} ...", messageHandler.getClass().getSimpleName(),
            messageHandler.getBinding());

      while (!Thread.currentThread().isInterrupted()) {
         String request = responder.recvStr();

         try {
            String response = messageHandler.reply(request);
            checkNotNull(response, "Post-condition violated: response must not be null.");
            checkArgument(response.length() > 0,
                  "Pre-condition violated: expression response.length() > 0 must be true.");

            logger.debug("{} --> in: [{}] out: [{}] ",
                  messageHandler.getClass().getSimpleName(),
                  request, abbreviate(response));
            responder.send(response, ZMQ.NOBLOCK);
         } catch (IllegalArgumentException e) {
            logger.warn("{} Caught {}: {}. Sending empty response ...",
                  messageHandler.getClass().getSimpleName(),
                  e.getClass().getSimpleName(),
                  e.getMessage());
            responder.send(NON_EMPTY_RESPONSE, ZMQ.NOBLOCK);
         } catch (Exception e) {
            logger.error(e.getMessage(), e);
            responder.send(NON_EMPTY_RESPONSE, ZMQ.NOBLOCK);
         }
      }

      // We never get here but clean up anyhow
      responder.close();
   }

   protected String abbreviate(String message) {
      if (message != null && message.length() > 10) {
         return message.substring(0, Math.min(10, message.length())) + " ...";
      }

      return message;
   }
}
