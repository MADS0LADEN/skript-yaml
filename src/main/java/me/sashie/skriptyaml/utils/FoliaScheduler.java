package me.sashie.skriptyaml.utils;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.vehicle.VehicleEvent;
import org.bukkit.event.world.ChunkEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Task helper that works on Paper as well as regionized platforms (Folia and CanvasMC).
 * <p>
 * Folia and CanvasMC do not have a single main thread, so {@code Bukkit.getScheduler()}
 * throws. Async work is sent to the async scheduler, and Skript continuations are
 * returned to the region that owns the current event when one can be inferred.
 */
public final class FoliaScheduler {

	private static final boolean REGIONIZED = detectRegionized();

	private FoliaScheduler() {
	}

	public static boolean isRegionized() {
		return REGIONIZED;
	}

	private static boolean detectRegionized() {
		if (classExists("io.papermc.paper.threadedregions.RegionizedServer"))
			return true;
		if (classExists("io.papermc.paper.threadedregions.ThreadedRegionizer"))
			return true;
		try {
			String name = Bukkit.getServer().getName();
			if (name != null) {
				String lower = name.toLowerCase(Locale.ROOT);
				if (lower.contains("folia") || lower.contains("canvas"))
					return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	private static boolean classExists(String name) {
		try {
			Class.forName(name);
			return true;
		} catch (ClassNotFoundException ex) {
			return false;
		}
	}

	/**
	 * Runs a task off the tick threads.
	 */
	public static void runAsync(Plugin plugin, Runnable task) {
		if (REGIONIZED) {
			Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
			return;
		}
		Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
	}

	/**
	 * Runs a task on the region that owns {@code event}, or on the global region
	 * when no location/entity context is available. On Paper/Spigot this is the
	 * main thread.
	 */
	public static void runSync(Plugin plugin, Event event, Runnable task) {
		if (!REGIONIZED) {
			Bukkit.getScheduler().runTask(plugin, task);
			return;
		}
		if (event instanceof PlayerEvent) {
			runOnEntity(plugin, ((PlayerEvent) event).getPlayer(), task);
			return;
		}
		if (event instanceof EntityEvent) {
			runOnEntity(plugin, ((EntityEvent) event).getEntity(), task);
			return;
		}
		if (event instanceof VehicleEvent) {
			runOnEntity(plugin, ((VehicleEvent) event).getVehicle(), task);
			return;
		}
		if (event instanceof BlockEvent) {
			runOnLocation(plugin, ((BlockEvent) event).getBlock().getLocation(), task);
			return;
		}
		if (event instanceof ChunkEvent) {
			Chunk chunk = ((ChunkEvent) event).getChunk();
			Bukkit.getRegionScheduler().execute(plugin, chunk.getWorld(), chunk.getX(), chunk.getZ(), task);
			return;
		}
		if (event instanceof InventoryEvent) {
			InventoryHolder holder = ((InventoryEvent) event).getInventory().getHolder();
			if (holder instanceof Entity) {
				runOnEntity(plugin, (Entity) holder, task);
				return;
			}
			if (holder instanceof BlockState) {
				runOnLocation(plugin, ((BlockState) holder).getLocation(), task);
				return;
			}
		}
		if (event instanceof WorldEvent) {
			runOnLocation(plugin, ((WorldEvent) event).getWorld().getSpawnLocation(), task);
			return;
		}
		runGlobal(plugin, task);
	}

	public static void runGlobal(Plugin plugin, Runnable task) {
		if (!REGIONIZED) {
			Bukkit.getScheduler().runTask(plugin, task);
			return;
		}
		Bukkit.getGlobalRegionScheduler().execute(plugin, task);
	}

	private static void runOnEntity(Plugin plugin, Entity entity, Runnable task) {
		if (entity == null) {
			runGlobal(plugin, task);
			return;
		}
		entity.getScheduler().run(plugin, scheduled -> task.run(), () -> runGlobal(plugin, task));
	}

	private static void runOnLocation(Plugin plugin, Location location, Runnable task) {
		if (location == null || location.getWorld() == null) {
			runGlobal(plugin, task);
			return;
		}
		Bukkit.getRegionScheduler().execute(plugin, location, task);
	}
}
