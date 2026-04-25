import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

class Game {
    Player player = new Player();
    int playerX = 5;
    int playerY = 5;
    int floor = 1;
    MoonCycle moonCycle = new MoonCycle();
    HashMap<String, Enemy> enemyDB;
    HashMap<String, Item> chestItemDB;

    Map map;
    Scanner sc = new Scanner(System.in);

    public Game() {
        this.enemyDB = EnemyLoader.loadEnemies("enemylist.csv");
        this.chestItemDB = ItemLoader.loadItems("ChestItems.csv");
        this.map = new Map(playerX, playerY, enemyDB);
        this.map.spawnChests(3);
    }

    public void initializePlayer() {
        clearScreen();
        System.out.println("--- WELCOME TO THE LABYRINTH ---");
        System.out.print("Load previous save? (y/n): ");
        String choice = sc.nextLine().toLowerCase();

        if (choice.equals("y")) {
            // If load is successful, we exit this method immediately
            if (loadGame()) {
                return; 
            }
            // If loadGame returned false, it continues down to character creation
            System.out.println("Proceeding to character creation...");
            pause();
        }

        // --- CHARACTER CREATION ---
        int bonusPoints = 5;
        while (bonusPoints > 0) {
            clearScreen();
            System.out.println("--- CHARACTER CREATION ---");
            System.out.println("Points remaining: " + bonusPoints);
            System.out.println("1. Strength: " + player.strength);
            System.out.println("2. Vitality: " + player.vitality);
            System.out.println("3. Luck:     " + player.luck);
            System.out.print("Select stat (1-3): ");

            try {
                int statChoice = Integer.parseInt(sc.nextLine());
                if (statChoice >= 1 && statChoice <= 3) {
                    player.addStat(statChoice);
                    bonusPoints--;
                }
            } catch (Exception e) { 
                System.out.println("Invalid input!"); 
            }
        }
        player.currentHp = player.getMaxHp();
    }

    public void start() {
        initializePlayer();
        while (true) {
            clearScreen();
            System.out.println("Floor: " + floor + " | Macca: " + player.macca);
            System.out.println("Moon: " + moonCycle.getCurrentPhase());
            map.draw(playerX, playerY);
            System.out.print("Move (WASD) or open (M)enu: ");
            String input = sc.nextLine().toLowerCase();
            if (input.equals("m")) openMainMenu();
            else move(input);

            if (!player.isAlive()) { resetGame(); continue; }
            if (map.isExit(playerX, playerY)) nextFloor();
        }
    }

    private void move(String input) {
        boolean moved = false;
        switch (input) {
            case "w": if (playerY > 0) { playerY--; moved = true; } break;
            case "s": if (playerY < 9) { playerY++; moved = true; } break;
            case "a": if (playerX > 0) { playerX--; moved = true; } break;
            case "d": if (playerX < 9) { playerX++; moved = true; } break;
        }
        if (moved) {
            moonCycle.playerMoved();

            Enemy enemy = map.getEnemyAt(playerX, playerY);
            if (enemy != null) startBattle(enemy);
            if (map.isChest(playerX, playerY)) {
                handleChest();
            }
        }


    }

    private void startBattle(Enemy enemy) {
        enemy.burnTicks = 0;
        enemy.skipTicks = 0;

        // 2. ENEMY SCALING (Every 2 Floors)
        int scalingTier = floor / 2; 
        int enemyMaxHP = ((1 + enemy.vitality) * 6) + (scalingTier * 20);
        int enemyCurrentHP = enemyMaxHP;
        int scaledStr = enemy.strength + (scalingTier * 2);

        while (enemyCurrentHP > 0 && player.isAlive()) {
            clearScreen();
            System.out.println("========================================");
            System.out.println("BATTLE: " + enemy.name + " | FLOOR: " + floor);
            System.out.println("========================================");
            System.out.println(String.format("  %-20s HP: %d/%d", enemy.name, enemyCurrentHP, enemyMaxHP));
            System.out.println("\n");
            System.out.println(String.format("  %-20s HP: %d/%d", "PLAYER", player.currentHp, player.getMaxHp()));
            System.out.println("========================================");
            System.out.println("  1. Attack    2. Skills    3. Items   4. Talk");
            System.out.println("========================================");
            System.out.print("Choose an action: ");

            String choice = sc.nextLine();

            // --- PLAYER TURN ---
            if (choice.equals("1")) {
                int damage = (player.level + player.strength) * 40 / 15;
                enemyCurrentHP -= damage;
                System.out.println("\n> You lunged at " + enemy.name + " for " + damage + " damage!");
                pause();
            } 
            else if (choice.equals("2")) {
                Item card = selectSkillCard();
                if (card != null) {
                    System.out.println("\n> You used " + card.name + "!");
                    enemyCurrentHP -= card.value;
                    
                    String cName = card.name.toLowerCase();
                    if (cName.contains("agi")) {
                        enemy.burnTicks = 3;
                        System.out.println("> The enemy is engulfed in flames!");
                    } else if (cName.contains("zio") || cName.contains("bufu")) {
                        enemy.skipTicks = 1;
                        System.out.println("> The enemy is Stunned!");
                    }
                    player.inventory.remove(card);
                    pause();
                } else {
                    continue; // Go back to menu if no card selected
                }
            } 
            else if (choice.equals("3")) {
                // Return true means "go back to main battle menu"
                if (handleInBattleHealing()) continue; 
            } 
            else if (choice.equals("4")) {
                // Negotiation check
                if (handleNegotiation(enemy)) return; // Ends battle immediately on success
            } 
            else {
                continue;
            }

            if (enemyCurrentHP <= 0) break;

            // --- ENEMY TURN ---
            System.out.println("\n--- " + enemy.name.toUpperCase() + "'S TURN ---");

            // Check Burn Status
            if (enemy.burnTicks > 0) {
                int burnDmg = 10;
                enemyCurrentHP -= burnDmg;
                System.out.println(">> " + enemy.name + " takes " + burnDmg + " burn damage!");
                enemy.burnTicks--;
                if (enemyCurrentHP <= 0) {
                    System.out.println(">> " + enemy.name + " succumbed to the flames!");
                    pause();
                    break;
                }
            }

            // Check Stun Status
            if (enemy.skipTicks > 0) {
                System.out.println(">> " + enemy.name + " is reeling and misses their turn!");
                enemy.skipTicks--;
                pause();
            } else {
                // Standard Scaled Attack
                int enemyDamage = (1 + scaledStr) * 40 / 15;
                player.takeDamage(enemyDamage);
                System.out.println("> " + enemy.name + " attacks! You took " + enemyDamage + " damage.");
                pause();
            }
        }

        // --- POST BATTLE ---
        handleBattleEnd(enemy);
    }

    private boolean handleNegotiation(Enemy enemy) {
        MoonCycle.Phase ph = moonCycle.getCurrentPhase();
        
        if (ph == MoonCycle.Phase.FULL) { 
            System.out.println("> The enemy is blinded by moon-rage! They won't talk!"); 
            pause(); 
            return false; 
        }
        if (ph == MoonCycle.Phase.NEW) { 
            processNegotiationSuccess(enemy); 
            return true; 
        }
        
        int chance = 20 + (player.luck * 2);
        if (new Random().nextInt(100) < chance) { 
            processNegotiationSuccess(enemy); 
            return true; 
        } else { 
            System.out.println("> Negotiation failed!"); 
            pause(); 
            return false; 
        }
    }

    private void processNegotiationSuccess(Enemy enemy) {
        List<Item> loot = new ArrayList<>(chestItemDB.values());
        Item gift = loot.get(new Random().nextInt(loot.size()));
        player.inventory.add(gift);
        System.out.println("> Received " + gift.name + "!");
        map.removeEnemy(playerX, playerY);
        pause();
    }

    private void handleBattleEnd(Enemy enemy) {
        if (player.isAlive()) {
            int gainedExp = (enemy.strength + enemy.vitality) * 2;
            int gainedMacca = (enemy.luck * 20); // Balanced to use Luck only
            player.exp += gainedExp; player.macca += gainedMacca;
            System.out.println("\nVictory! Gained " + gainedExp + " EXP.");
            while (player.exp >= 100) { player.exp -= 100; handleLevelUpMenu(); }
            map.removeEnemy(playerX, playerY);
            pause();
        }
    }

    private void saveGame() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("savegame.txt"))) {
            writer.println(floor);
            writer.println(player.level);
            writer.println(player.strength);
            writer.println(player.vitality);
            writer.println(player.luck);
            writer.println(player.macca);
            writer.println(player.exp);
            System.out.println("Save successful!");
            pause();
        } catch (IOException e) { System.out.println("Save failed."); }
    }

    private boolean loadGame() {
        File saveFile = new File("savegame.txt");
        if (!saveFile.exists()) {
            System.out.println("No save found!");
            pause();
            return false; // Tell initializePlayer we failed
        }

        try (Scanner fs = new Scanner(saveFile)) {
            floor = fs.nextInt();
            player.level = fs.nextInt();
            player.strength = fs.nextInt();
            player.vitality = fs.nextInt();
            player.luck = fs.nextInt();
            player.macca = fs.nextInt();
            player.exp = fs.nextInt();
            
            player.currentHp = player.getMaxHp();
            
            this.map = new Map(playerX, playerY, enemyDB);
            this.map.spawnChests(3);

            System.out.println("Load successful! Floor: " + floor);
            pause();
            return true; // Success
        } catch (Exception e) { 
            System.out.println("Error reading save file."); 
            pause(); 
            return false; // Error occurred
        }
    }

    private void openMainMenu() {
        boolean inMenu = true;
        while (inMenu) {
            clearScreen();
            System.out.println("--- CAMP MENU ---");
            System.out.println("Floor: " + floor + " | HP: " + player.currentHp + "/" + player.getMaxHp());
            System.out.println("1. Status  2. Inventory  3. Save  4. Exit");
            String choice = sc.nextLine();
            switch (choice) {
                case "1": showDetailedStatus(); break;
                case "2": openInventoryMenu(); break;
                case "3": saveGame(); break;
                case "4": inMenu = false; break;
            }
        }
    }

    private void showDetailedStatus() {
        clearScreen();
        System.out.println("--- STATUS ---");
        System.out.println("St: " + player.strength + " | Vi: " + player.vitality + " | Lu: " + player.luck);
        System.out.println("EXP: " + player.exp + "/100");
        pause();
    }

    private void nextFloor() {
        floor++; playerX = 5; playerY = 5;
        map = new Map(playerX, playerY, enemyDB);
        map.spawnChests(3);
        System.out.println("Descending to Floor " + floor + "...");
        pause();
    }

    private void clearScreen() { System.out.print("\033[H\033[2J"); System.out.flush(); }
    private void pause() { System.out.println("Press Enter..."); sc.nextLine(); }

    private void openInventoryMenu() {
        clearScreen();
        for (int i = 0; i < player.inventory.size(); i++) System.out.println((i+1) + ". " + player.inventory.get(i).name);
        System.out.println("0. Back");
        try {
            int c = Integer.parseInt(sc.nextLine()) - 1;
            if (c != -1) player.useItem(player.inventory.get(c));
        } catch (Exception e) {}
    }

    private Item selectSkillCard() {
        List<Item> cards = new ArrayList<>();
        for (Item i : player.inventory) if (i.type.equals("Skill")) cards.add(i);
        if (cards.isEmpty()) { System.out.println("No skill cards!"); pause(); return null; }
        for (int i = 0; i < cards.size(); i++) System.out.println((i+1) + ". " + cards.get(i).name);
        try { return cards.get(Integer.parseInt(sc.nextLine()) - 1); } catch (Exception e) { return null; }
    }

    private void handleLevelUpMenu() {
        player.levelUp();
        System.out.println("Level Up! Level " + player.level);
        System.out.println("1. St  2. Vi  3. Lu");
        try { player.addStat(Integer.parseInt(sc.nextLine())); } catch (Exception e) {}
    }

    private void resetGame() {
        clearScreen();
        System.out.println("========================================");
        System.out.println("               GAME OVER");
        System.out.println("========================================");
        pause();

        // 1. Completely reset the player object
        this.player = new Player();
        
        // 2. Reset world variables
        this.floor = 1;
        this.playerX = 5;
        this.playerY = 5;
        this.moonCycle = new MoonCycle();

        // 3. CRITICAL: Rebuild the map and spawn fresh chests
        this.map = new Map(playerX, playerY, enemyDB);
        this.map.spawnChests(3); 

        // 4. Send them back to the start
        initializePlayer();
    }

    private void handleChest() {
        clearScreen();
        System.out.println("========================================");
        System.out.println("      *** TREASURE CHEST! ***");
        System.out.println("========================================");

        // Convert the database values into a list to pick one randomly
        List<Item> possibleLoot = new ArrayList<>(chestItemDB.values());

        if (possibleLoot.isEmpty()) {
            System.out.println("> The chest is dusty and empty...");
        } else {
            // Pick a random item from your ChestItems.csv database
            Item foundItem = possibleLoot.get(new Random().nextInt(possibleLoot.size()));
            
            // Add it to the player's inventory
            player.inventory.add(foundItem);
            
            System.out.println("> You opened the chest and found:");
            System.out.println("  [" + foundItem.name + "] - " + foundItem.type);
        }

        // IMPORTANT: This removes the chest from the map so you don't 
        // open it again immediately.
        map.clearTile(playerX, playerY); 
        
        System.out.println("========================================");
        pause();
    }

    private boolean handleInBattleHealing() {
        // 1. Filter the inventory to show only HP healing items
        List<Item> heals = new ArrayList<>();
        for (Item i : player.inventory) {
            if (i.type.equals("HealHP")) {
                heals.add(i);
            }
        }

        // 2. If no healing items, tell the player and go back to the battle menu
        if (heals.isEmpty()) {
            System.out.println("\n> You have no healing items!");
            pause();
            return true; // "true" tells the battle loop to restart the menu
        }

        // 3. Display the healing menu
        System.out.println("\n--- USE HEALING ITEM ---");
        for (int i = 0; i < heals.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + heals.get(i).name + " (+" + heals.get(i).value + " HP)");
        }
        System.out.println("  0. Back");
        System.out.print("Select an item: ");

        try {
            int hChoice = Integer.parseInt(sc.nextLine());
            
            if (hChoice == 0) return true; // Go back to the battle menu

            // Use the selected item
            Item selectedItem = heals.get(hChoice - 1);
            player.useItem(selectedItem); // This calls the method in Player.java
            
            pause();
            return false; // "false" means a turn was used; the enemy will now attack
        } catch (Exception e) {
            System.out.println("Invalid selection.");
            pause();
            return true;
        }
    }
}
//adding final comment to make sure all files are updated