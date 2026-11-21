package lbvi;

public enum VegHealth {
    VERYHEALTHY(4, "Very Healthy"),
    HEALTHY(3,     "Healthy"),
    STRESS(2,      "Stressed"),
    SEVERELYSTRESSED(1, "Severely Stressed"),
    DEAD(0,        "Dead");

    private final int code;
    private final String label;

    VegHealth(int code, String label) {
        this.code = code;
        this.label = label;
    }

    //Getter
    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    // Reverse lookup: integer → enum
    public static VegHealth fromCode(int code) {
        for (VegHealth level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown veg state code: " + code);
    }

    // Reverse lookup by CSV label (case-insensitive)
    public static VegHealth fromLabel(String label) {
        String normalized = label.trim();
        for (VegHealth level : values()) {
            if (level.label.equalsIgnoreCase(normalized)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown VegHealth label: " + label);
    }
}
