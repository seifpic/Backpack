package com.almuramc.backpack.bukkit.storage.mode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.almuramc.backpack.bukkit.api.BackpackLoadEvent;
import com.almuramc.backpack.bukkit.api.BackpackSaveEvent;
import com.almuramc.backpack.bukkit.storage.Storage;
import com.almuramc.backpack.bukkit.storage.StorageMode;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class YamlFileStorage extends Storage {
	private static File BACKPACK_ROOT;
	private static final YamlConfiguration reader = new YamlConfiguration();

	public YamlFileStorage(File parentDir) {
		BACKPACK_ROOT = new File(parentDir, "backpacks");
	}

	@Override
	public StorageMode getMode() {
		return StorageMode.FILE;
	}

	@Override
	public void setup() {
		if (!BACKPACK_ROOT.exists()) {
			BACKPACK_ROOT.mkdir();
		}
		List<World> worlds = Bukkit.getWorlds();
		for (World world : worlds) {
			File subdir;
			try {
				subdir = new File(BACKPACK_ROOT.getCanonicalPath(), world.getName());
				if (!subdir.exists()) {
					subdir.mkdir();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public Inventory getBackpackFor(Player player, World world) {
		Inventory currentBackpack = (Inventory) INVENTORIES.get(player, world);
		//If they have a null backpack, assume they don't have it loaded from disk and try to fetch it.
		if (currentBackpack == null || currentBackpack.getContents().length <= 0) {
			currentBackpack = loadFromFile(player, world);
		}
		BackpackLoadEvent event = new BackpackLoadEvent(player, world, currentBackpack);
		Inventory backpack = event.getBackpack();
		//If they still have a null backpack by this point, assume they will not have a backpack period.
		if (backpack == null || event.isCancelled()) {
			return null;
		}
		if (!currentBackpack.equals(backpack)) {
			INVENTORIES.put(player, world, event.getBackpack());
		}
		return backpack;
	}

	@Override
	public void setBackpackFor(Player player, World world, Inventory inventory) {
		BackpackSaveEvent event = new BackpackSaveEvent(player, world, inventory);
		Inventory backpack = event.getBackpack();
		//Cancel saving but don't delete files
		if (event.isCancelled()) {
			return;
		} else {
			if (backpack == null || backpack.getContents().length <= 0) {
				INVENTORIES.remove(player, world);
			}
			saveToFile(player, world, inventory);
		}
	}

	private Inventory loadFromFile(Player player, World world) {
		File worldDir = new File(BACKPACK_ROOT, world.getName());
		File playerDat = null;
		if (worldDir == null) {
			return null;
		}
		for (File file : worldDir.listFiles()) {
			if (!file.getName().contains(".yml")) {
				continue;
			}
			String name = (file.getName().split(".yml"))[0];
			if (!name.equals(player.getName())) {
				continue;
			}
			playerDat = new File(worldDir, file.getName());
		}

		//No file was found for this player, return a blank empty inventory then.
		if (playerDat == null) {
			return Bukkit.createInventory(player, 54, "Backpack"); //TODO return size based on player or global perm
		}

		//File found, lets load in contents
		try {
			reader.load(playerDat);
			ArrayList<ItemStack> items = new ArrayList<ItemStack>();
			ConfigurationSection parent = reader.getConfigurationSection("backpack");
			for (String key : parent.getKeys(false)) {
				ConfigurationSection sub = parent.getConfigurationSection(key);
				ItemStack item = new ItemStack(Material.getMaterial(key), sub.getInt("amount"), ((Integer) sub.get("durability")).shortValue(), ((Integer) sub.get("data")).byteValue());
				items.add(item);
			}

			Inventory backpack = Bukkit.createInventory(player, 54, "Backpack");
			backpack.setContents(items.toArray(new ItemStack[items.size()]));
			return backpack;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private void saveToFile(Player player, World world, Inventory backpack) {
		File playerBackpack = new File(BACKPACK_ROOT + File.separator + world.getName(), player.getName() + ".yml");
		try {
			//Delete the current file (it saves a lot of hassle and code, just delete and remake with contents)
			if (playerBackpack.exists()) {
				playerBackpack.delete();
			}
			//Stop saving if null backpack
			if (backpack == null) {
				return;
			}
			//If creating the new file failed for some reason stop saving.
			if (!playerBackpack.createNewFile()) {
				return;
			}
			reader.load(playerBackpack);
			reader.createSection("backpack");
			ItemStack[] stacks = backpack.getContents();
			for (ItemStack stack : stacks) {
				if (stack == null) {
					continue;
				}
				ConfigurationSection section = reader.getConfigurationSection("backpack").createSection(stack.getType().name());
				section.set("amount", stack.getAmount());
				section.set("durability", stack.getDurability());
				section.set("data", stack.getData().getData());
				section.set("enchantments", stack.getEnchantments());
			}
			reader.save(playerBackpack);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}