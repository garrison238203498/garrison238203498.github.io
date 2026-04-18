public enum HazardLevel {
    LOW("Low"),
    MEDIUM("Moderate"),
    HIGH("Severe");

    private final String label;

    HazardLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
