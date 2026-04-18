public class Room {
    private final String id;
    private final String name;
    private final int x;
    private final int y;
    private final HazardLevel hazard;
    private final String summary;

    public Room(String id, String name, int x, int y, HazardLevel hazard, String summary) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.hazard = hazard;
        this.summary = summary;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public HazardLevel getHazard() {
        return hazard;
    }

    public String getSummary() {
        return summary;
    }
}
