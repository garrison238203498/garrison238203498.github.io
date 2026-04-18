public class Command {
    private final CommandType type;
    private final String target;

    public Command(CommandType type, String target) {
        this.type = type;
        this.target = target;
    }

    public CommandType getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }
}