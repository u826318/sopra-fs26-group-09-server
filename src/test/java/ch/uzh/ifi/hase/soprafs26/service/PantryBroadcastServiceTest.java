package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.websocket.PantryUpdateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class PantryBroadcastServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PantryBroadcastService pantryBroadcastService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void broadcastPantryUpdate_sendsToCorrectTopic() {
        Long householdId = 1L;
        PantryUpdateMessage message = new PantryUpdateMessage();
        message.setEventType("ITEM_ADDED");
        message.setHouseholdId(householdId);

        pantryBroadcastService.broadcastPantryUpdate(householdId, message);

        Mockito.verify(messagingTemplate, Mockito.times(1))
                .convertAndSend("/topic/household/1/pantry", message);
    }
}
