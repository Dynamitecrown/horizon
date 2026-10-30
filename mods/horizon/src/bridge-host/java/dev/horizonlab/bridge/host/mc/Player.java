package dev.horizonlab.bridge.host.mc;

import java.util.ArrayList;
import java.util.List;

public class Player {
    /** System messages sent to this player, for Horizon to show in chat. */
    public final List<String> messages = new ArrayList<>();

    public void sendSystemMessage(Component message) { messages.add(message.text()); }
}
