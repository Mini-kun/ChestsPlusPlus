package com.jamesdpeters.minecraft.chests.interfaces;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.crafting.Crafting;
import com.jamesdpeters.minecraft.chests.misc.Utils;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.serialize.LocationInfo;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class VirtualCraftingHolder implements InventoryHolder {

    private final Inventory inventory;
    private final AutoCraftingStorage storage;
    private BukkitRunnable guiTask;
    private BukkitRunnable craftItemTask;

    private ItemStack[][] recipeChoices = new ItemStack[9][];
    private ItemStack result;
    private final int[] recipeChoiceIndex = new int[9];
    private boolean hasCompleteRecipe = false;

    /**
     * This gets set to true when updateCrafting() gets called on a 1 tick delay.
     */
    private boolean isUpdatingRecipe = false;

    private static final ItemStack AIR = new ItemStack(Material.AIR);

    public VirtualCraftingHolder(AutoCraftingStorage storage) {
        this.storage = storage;
        inventory = Bukkit.createInventory(this, InventoryType.WORKBENCH, storage.getIdentifier());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }


    public AutoCraftingStorage getStorage() {
        return storage;
    }

    public void setCrafting(ShapelessRecipe shapelessRecipe) {
        result = shapelessRecipe.getResult();
        List<RecipeChoice> choiceList = shapelessRecipe.getChoiceList();
        for (int i = 0; i < choiceList.size(); i++) {
            RecipeChoice recipeChoice = choiceList.get(i);
            if (recipeChoice instanceof RecipeChoice.MaterialChoice) {
                RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) recipeChoice;
                ItemStack[] choices = materialChoice.getChoices().stream().map(ItemStack::new).toArray(ItemStack[]::new);
                recipeChoices[i] = choices;
            }
        }
        setHasCompleteRecipe();
    }

    public void setCrafting(ShapedRecipe recipe) {
        result = recipe.getResult();
        int row = 0;
        for (String r : recipe.getShape()) {
            int col = 0;
            for (char c : r.toCharArray()) {
                RecipeChoice recipeChoice = recipe.getChoiceMap().get(c);
                if (recipeChoice instanceof RecipeChoice.MaterialChoice) {
                    RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) recipeChoice;
                    ItemStack[] choices = materialChoice.getChoices().stream().map(ItemStack::new).toArray(ItemStack[]::new);
                    int i = (row * 3) + col;
                    recipeChoices[i] = choices;
                }
                col++;
            }
            row++;
        }
        setHasCompleteRecipe();
    }

    public void setCrafting(Recipe recipe) {
        if (recipe instanceof ShapedRecipe) setCrafting((ShapedRecipe) recipe);
        if (recipe instanceof ShapelessRecipe) setCrafting((ShapelessRecipe) recipe);
    }

    private void setHasCompleteRecipe() {
        hasCompleteRecipe = true;
        startCraftingItems();
    }

    public void resetChoices() {
        recipeChoices = new ItemStack[9][];
        result = null;
        hasCompleteRecipe = false;
        stopCraftingItems();
    }

    public void updateCrafting() {
        List<ItemStack> craftingMatrix = new ArrayList<>(Arrays.asList(inventory.getContents()));
        if (craftingMatrix.get(0) != null) craftingMatrix.remove(0);
        Recipe recipe = Crafting.getResult(craftingMatrix);
        getStorage().setRecipe(recipe);
        resetChoices();
        if (recipe != null) {
            setCrafting(recipe);
            playSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1f);
        } else {
            stopCraftingItems();
        }
        isUpdatingRecipe = false;
        updateGUI();
        storage.onItemDisplayUpdate(result);
    }

    private void playSound(Sound sound, float volume, float pitch) {
        storage.getInventory().getViewers().forEach(humanEntity -> {
            humanEntity.getWorld().playSound(humanEntity.getLocation(), sound, volume, pitch);
        });
    }

    public VirtualCraftingHolder setUpdatingRecipe(boolean updatingRecipe) {
        isUpdatingRecipe = updatingRecipe;
        return this;
    }

    //Start and stop animation based on if the inventory is open.
    public void startAnimation() {
        guiTask = new UpdateTask();
    }

    public void stopAnimation() {
        if (guiTask != null) guiTask.cancel();
    }

    private class UpdateTask extends BukkitRunnable {
        BukkitTask task;

        public UpdateTask() {
            task = runTaskTimer(ChestsPlusPlus.PLUGIN, 1, 15);
        }

        @Override
        public void run() {
            updateGUI();
        }
    }

    public void updateGUI() {
        inventory.setItem(0, result);
        if (hasCompleteRecipe && !isUpdatingRecipe) {
            for (int i = 0; i < 9; i++) {
                ItemStack[] choices = recipeChoices[i];
                if (choices != null) {
                    int index = recipeChoiceIndex[i];
                    ItemStack choice;
                    if (index < choices.length) {
                        choice = choices[index];
                    } else {
                        recipeChoiceIndex[i] = 0;
                        choice = choices[0];
                    }
                    inventory.setItem(i + 1, choice);
                    recipeChoiceIndex[i]++;
                } else {
                    inventory.setItem(i + 1, null);
                }
            }
        }
    }

    public void startCraftingItems() {
        if (craftItemTask == null || craftItemTask.isCancelled()) craftItemTask = new CraftItems();
    }

    public void stopCraftingItems() {
        if (craftItemTask != null) craftItemTask.cancel();
    }

    private class CraftItems extends BukkitRunnable {
        BukkitTask task;

        CraftItems() {
            task = runTaskTimer(ChestsPlusPlus.PLUGIN, 1, 20);
        }

        @Override
        public void run() {
            craftItem();
        }
    }

    /**
     * This method will craft an item if a chest above contains the correct amount of materials
     * And there is a hopper below.
     */
    public void craftItem() {
        for (LocationInfo location : storage.getLocations()) {
            if (!Utils.isLocationChunkLoaded(location.getLocation())) continue;
            Block block = location.getLocation().getBlock();
            Block blockBelow = block.getRelative(BlockFace.DOWN);
            Block blockAbove = block.getRelative(BlockFace.UP);

            Inventory output;

            if (blockBelow.getState() instanceof Hopper) {
                Hopper hopper = (Hopper) blockBelow.getState();
                if (blockBelow.isBlockPowered() || blockBelow.isBlockIndirectlyPowered()) {
                    continue; //If hopper is powered no crafting should happen.
                }
                output = hopper.getInventory();
            } else {
                output = getInventory(blockBelow);
                //If there is no output crafting isn't possible, so skip this location.
                if (output == null) continue;
                //If crafting table is powered output into container is possible.
                if (!block.isBlockPowered()) continue;
            }

            List<Inventory> inventories = new ArrayList<>();
            Utils.addIfNotNull(inventories, getInventory(blockAbove));
            Utils.addIfNotNull(inventories, getInventory(block.getRelative(BlockFace.NORTH)));
            Utils.addIfNotNull(inventories, getInventory(block.getRelative(BlockFace.EAST)));
            Utils.addIfNotNull(inventories, getInventory(block.getRelative(BlockFace.SOUTH)));
            Utils.addIfNotNull(inventories, getInventory(block.getRelative(BlockFace.WEST)));

            boolean didCraft = craftItem(inventories, output);

            //Play sound if crafting occured.
            if (didCraft) {
                if (location.getLocation().getWorld() != null) {
                    location.getLocation().getWorld().playSound(location.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 0.25f, 1f);
                    if (output.getHolder() instanceof VirtualInventoryHolder) {
                        ((VirtualInventoryHolder) output.getHolder()).getStorage().updateDisplayItem();
                    }
                }
            }
        }
    }

    private Inventory getInventory(Block block) {
        Inventory inventory = null;
        if (block.getState() instanceof Container) {
            ChestLinkStorage storage = Config.getChestLink().getStorage(block.getLocation());
            //Check if a ChestLink exists above the CraftingTable and if the owner of the CraftingTable has permission to access that Chest.
            if (storage != null) {
                if (storage.hasPermission(this.storage.getOwner())) {
                    inventory = storage.getInventory();
                } else {
                    return null;
                }
            } else {
                inventory = ((Container) block.getState()).getInventory();
            }
        }
        return inventory;
    }

    private boolean craftItem(List<Inventory> inputs, Inventory output) {
        boolean sameInv = false;
        Inventory sameInventory = null;
        List<Inventory> tempInvs = new ArrayList<>();

        for (Inventory inv : inputs) {
            Inventory tempInv = Utils.copyInventory(inv);
            tempInvs.add(tempInv);
            if (inv.equals(output)) {
                sameInv = true;
                sameInventory = tempInv;
            }
        }

        for (ItemStack[] choices : recipeChoices) {
            if (choices == null) continue;
            boolean foundMatch = false;
            for (ItemStack choice : choices) {
                for (Inventory tempInv : tempInvs) {
                    int index = tempInv.first(choice.getType());
                    if (index != -1) {
                        ItemStack item = tempInv.getItem(index);
                        if (item != null) {
                            item.setAmount(item.getAmount() - 1);
                            tempInv.setItem(index, item);
                            foundMatch = true;
                            break;
                        }
                    }
                }
            }
            //If no match
            if (!foundMatch) return false;
        }

        //If we reach here there are enough materials so check for space in the Hopper and update inventory.
        //Check if output and input are the same inventory to avoid duplication.
        Inventory tempOutput = sameInv ? sameInventory : Utils.copyInventory(output);
        HashMap map = tempOutput.addItem(result.clone());

        //If result fits into output copy over the temporary inventories.
        if (map.isEmpty()) {
            for (int i = 0; i < tempInvs.size(); i++) {
                moveTempInv(tempInvs.get(i), inputs.get(i));
            }
            if (!sameInv) moveTempInv(tempOutput, output);
            return true;
        }
        return false;
    }

    private void moveTempInv(Inventory tempInv, Inventory realInv) {
        ItemStack[] contents = new ItemStack[realInv.getSize()];
        System.arraycopy(tempInv.getContents(), 0, contents, 0, realInv.getSize());
        realInv.setContents(contents);
    }

    public void forceUpdateInventory() {
        inventory.getViewers().forEach(humanEntity -> {
            if (humanEntity instanceof Player) {
                ((Player) humanEntity).updateInventory();
            }
        });
    }

}
