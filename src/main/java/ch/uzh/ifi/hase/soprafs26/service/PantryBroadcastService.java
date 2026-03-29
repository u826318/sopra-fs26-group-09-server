package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.websocket.PantryUpdateMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PantryBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public PantryBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastPantryUpdate(Long householdId, PantryUpdateMessage message) {
        messagingTemplate.convertAndSend(
            "/topic/household/" + householdId + "/pantry",
            message
        );
    }
}
