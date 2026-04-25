import java.util.ArrayList;

public class Player {
    public int level = 5;
    public int strength = 5, vitality = 5, luck = 5; // Removed Magic and Agility
    public int macca = 0, exp = 0;
    public int currentHp;
    public ArrayList<Item> inventory = new ArrayList<>();

    public Player() {
        this.currentHp = getMaxHp();
    }

    public int getMaxHp() {
        return (level + vitality) * 6;
    }

    // Updated to only allow 1 (St), 2 (Vi), or 3 (Lu)
    public void addStat(int choice) {
        switch(choice) {
            case 1: strength++; break;
            case 2: vitality++; break;
            case 3: luck++; break;
        }
        this.currentHp = getMaxHp(); // Heal on stat increase
    }

    public void takeDamage(int dmg) {
        this.currentHp = Math.max(0, this.currentHp - dmg);
    }

    public boolean isAlive() { return this.currentHp > 0; }

    public void useItem(Item item) {
        switch (item.type) {
            case "HealHP":
                int max = getMaxHp();
                this.currentHp = Math.min(max, this.currentHp + item.value);
                System.out.println("Used " + item.name + ". Restored " + item.value + " HP!");
                break;
                
            case "Stat":
                handleStatBoost(item);
                break;

            default:
                System.out.println("This item cannot be used right now.");
                return; // Don't remove the item if it wasn't used
        }
        // Remove the item after successful use
        inventory.remove(item);
    }

    public void levelUp() {
        this.level++;
        this.currentHp = getMaxHp();
    }

    private void handleStatBoost(Item item) {
        String itemName = item.name.toLowerCase();
        
        if (itemName.contains("str") || itemName.contains("strength")) {
            this.strength += item.value;
            System.out.println("Strength increased to " + this.strength + "!");
        } else if (itemName.contains("vit") || itemName.contains("vitality")) {
            this.vitality += item.value;
            // Since Max HP is calculated based on Vitality, we refresh currentHp
            this.currentHp = getMaxHp(); 
            System.out.println("Vitality increased! Max HP is now " + getMaxHp());
        } else if (itemName.contains("luck")) {
            this.luck += item.value;
            System.out.println("Luck increased to " + this.luck + "!");
        }
    }
}
//adding final comment to make sure all files are updated