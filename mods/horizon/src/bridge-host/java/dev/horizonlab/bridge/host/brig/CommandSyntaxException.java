package dev.horizonlab.bridge.host.brig;

public class CommandSyntaxException extends Exception {
    private static final long serialVersionUID = 1L;

    public CommandSyntaxException(Message message) { super(text(message)); }

    private static String text(Message message) {
        return message instanceof dev.horizonlab.bridge.host.mc.Component component ? component.text() : String.valueOf(message);
    }
}
