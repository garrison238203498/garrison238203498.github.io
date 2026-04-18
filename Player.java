import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Player {
    private final Map<String, Item> inventory;
    private int score;
    private boolean maskEquipped;

    public Player() {
        inventory = new LinkedHashMap<>();
        score = 0;
        maskEquipped = false;

        addItem(new Item("mirror"));
        addItem(new Item("note"));
    }

    public void addItem(Item item) {
        inventory.put(item.getName(), item);
    }

    public boolean hasItem(String itemName) {
        return inventory.containsKey(itemName);
    }

    public void removeItem(String itemName) {
        inventory.remove(itemName);
    }

    public void addScore(int points) {
        score += points;
    }

    public int getScore() {
        return score;
    }

    public boolean isMaskEquipped() {
        return maskEquipped;
    }

    public void setMaskEquipped(boolean equipped) {
        maskEquipped = equipped;
    }

    public void showInventory() {
        showInventory(System.out::println);
    }

    public void showInventory(Consumer<String> output) {
        if (inventory.isEmpty()) {
            output.accept("Inventory: empty");
            return;
        }

        output.accept("Inventory:");
        for (String name : inventory.keySet()) {
            output.accept("- " + name);
        }
    }

    public List<String> getInventoryNames() {
        return new ArrayList<>(inventory.keySet());
    }
}
