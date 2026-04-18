import java.util.HashMap;
import java.util.Map;

public class CommandParser {
    private final Map<String, CommandType> simpleCommands;

    public CommandParser() {
        simpleCommands = new HashMap<>();
        simpleCommands.put("look", CommandType.LOOK);
        simpleCommands.put("help", CommandType.HELP);
        simpleCommands.put("inventory", CommandType.INVENTORY);
        simpleCommands.put("status", CommandType.STATUS);
        simpleCommands.put("map", CommandType.MAP);
        simpleCommands.put("quit", CommandType.QUIT);
    }

    public Command parse(String rawInput) {
        String input = clean(rawInput);

        if (input.isEmpty()) {
            return new Command(CommandType.UNKNOWN, "");
        }

        Direction simpleDirection = Direction.fromText(input);
        if (simpleDirection != null) {
            return new Command(CommandType.MOVE, simpleDirection.getLabel());
        }

        if (simpleCommands.containsKey(input)) {
            return new Command(simpleCommands.get(input), "");
        }

        String[] parts = input.split("\\s+", 2);
        String verb = parts[0];
        String target = parts.length > 1 ? parts[1].trim() : "";

        switch (verb) {
            case "go":
            case "move":
                return new Command(CommandType.MOVE, normalizeDirection(target));
            case "examine":
            case "inspect":
            case "read":
                return new Command(CommandType.EXAMINE, target);
            case "use":
                return new Command(CommandType.USE, target);
            case "solve":
                return new Command(CommandType.SOLVE, target);
            case "escape":
                return new Command(CommandType.ESCAPE, target);
            default:
                return new Command(CommandType.UNKNOWN, input);
        }
    }

    public String normalizeObject(String rawObject) {
        String object = clean(rawObject);

        if (containsAny(object, "panel", "console", "relay")) return "panel";
        if (containsAny(object, "grate", "vent")) return "grate";
        if (containsAny(object, "toolbox", "tools")) return "toolbox";
        if (containsAny(object, "safe")) return "safe";
        if (containsAny(object, "mask", "oxygen")) return "mask";
        if (containsAny(object, "monitor", "screen", "status")) return "monitor";
        if (containsAny(object, "locker")) return "locker";
        if (containsAny(object, "pod", "hatch")) return "pod";
        if (containsAny(object, "door", "main controls", "command lock", "main lock")) return "main door";
        if (containsAny(object, "logs", "terminal", "console")) return "logs";
        if (containsAny(object, "route map", "map", "junction map")) return "route map";
        if (containsAny(object, "shelf", "filter shelf", "emergency shelf")) return "filter shelf";
        if (containsAny(object, "coil")) return "relay coil";
        if (containsAny(object, "patch")) return "filter patch";
        if (containsAny(object, "fuse")) return "spare fuse";
        if (containsAny(object, "note")) return "note";
        if (containsAny(object, "mirror")) return "mirror";

        return object;
    }

    public String normalizeItem(String rawItem) {
        String item = clean(rawItem);

        if (containsAny(item, "mirror")) return "mirror";
        if (containsAny(item, "note")) return "note";
        if (containsAny(item, "screwdriver")) return "screwdriver";
        if (containsAny(item, "crowbar")) return "crowbar";
        if (containsAny(item, "fuse")) return "fuse";
        if (containsAny(item, "card")) return "access card";
        if (containsAny(item, "battery", "cell")) return "battery";
        if (containsAny(item, "launch key")) return "launch key";
        if (containsAny(item, "mask", "oxygen")) return "oxygen mask";
        if (containsAny(item, "override")) return "override code";
        if (containsAny(item, "door code") || item.equals("code")) return "main door code";
        if (containsAny(item, "patch")) return "filter patch";
        if (containsAny(item, "coil")) return "relay coil";

        return item;
    }

    public String normalizeDirection(String rawDirection) {
        Direction direction = Direction.fromText(rawDirection);
        return direction == null ? clean(rawDirection) : direction.getLabel();
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }

        return text.toLowerCase()
                   .trim()
                   .replace("[", "")
                   .replace("]", "");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
