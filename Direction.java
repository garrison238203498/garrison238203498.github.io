public enum Direction {
    NORTH("north", "n", 0, -1),
    SOUTH("south", "s", 0, 1),
    EAST("east", "e", 1, 0),
    WEST("west", "w", -1, 0);

    private final String label;
    private final String shortLabel;
    private final int dx;
    private final int dy;

    Direction(String label, String shortLabel, int dx, int dy) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.dx = dx;
        this.dy = dy;
    }

    public String getLabel() {
        return label;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public static Direction fromText(String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim().toLowerCase();
        for (Direction direction : values()) {
            if (direction.label.equals(value) || direction.shortLabel.equals(value)) {
                return direction;
            }
        }
        return null;
    }
}
