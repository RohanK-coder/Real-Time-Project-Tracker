package com.example.tracker.realtime;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * Sends the real authorization reason in the STOMP ERROR frame (e.g. "Not a member of board 7"),
 * instead of Spring's generic "Failed to send message to clientInboundChannel". Other errors keep the
 * default (non-revealing) handling.
 */
public class StompErrorHandler extends StompSubProtocolErrorHandler {
    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
        if (cause instanceof AccessDeniedException) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
            accessor.setMessage("ACCESS_DENIED: " + cause.getMessage());
            accessor.setLeaveMutable(true);
            return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }
        return super.handleClientMessageProcessingError(clientMessage, ex);
    }
}
