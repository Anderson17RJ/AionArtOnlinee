package com.aionemu.gameserver.world.geo.navmesh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.geo.navmesh.Al40NavMesh.PathResult;

/** Indexes AL40 files at startup and keeps meshes in memory only while their world has players. */
public final class NavMeshService {

	private static final Logger log = LoggerFactory.getLogger(NavMeshService.class);
	private static final Path NAVMESH_DIRECTORY = Path.of("data/geo");
	private static final long UNLOAD_DELAY_MS = 60_000;

	private volatile Map<Integer, Path> availableFiles = Map.of();
	private final Map<Integer, Al40NavMesh> loadedMeshes = new ConcurrentHashMap<>();
	private final Map<Integer, AtomicInteger> playerCounts = new ConcurrentHashMap<>();
	private final Map<Integer, AtomicInteger> unloadGenerations = new ConcurrentHashMap<>();
	private final Map<Integer, ScheduledFuture<?>> unloadTasks = new ConcurrentHashMap<>();
	private final java.util.Set<Integer> loadingWorlds = ConcurrentHashMap.newKeySet();
	private final java.util.Set<Integer> failedWorlds = ConcurrentHashMap.newKeySet();
	private final Object loadLock = new Object();

	public synchronized void init() {
		loadedMeshes.clear();
		playerCounts.clear();
		unloadGenerations.clear();
		unloadTasks.values().forEach(task -> task.cancel(false));
		unloadTasks.clear();
		loadingWorlds.clear();
		failedWorlds.clear();

		if (!Files.isDirectory(NAVMESH_DIRECTORY)) {
			availableFiles = Map.of();
			log.warn("Navmesh directory {} does not exist; NPCs will use legacy movement", NAVMESH_DIRECTORY);
			return;
		}

		Map<Integer, Path> discovered = new HashMap<>();
		try (Stream<Path> files = Files.list(NAVMESH_DIRECTORY)) {
			files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".nav")).sorted()
				.forEach(path -> {
					String filename = path.getFileName().toString();
					try {
						int worldId = Integer.parseInt(filename.substring(0, filename.length() - 4));
						discovered.put(worldId, path);
					} catch (NumberFormatException e) {
						log.warn("Ignoring navmesh with non-numeric name: {}", filename);
					}
				});
		} catch (IOException e) {
			log.error("Could not scan " + NAVMESH_DIRECTORY + " for navmeshes", e);
		}
		availableFiles = Map.copyOf(discovered);
		log.info("Indexed {} AL40 navmesh files; maps will be loaded while players are present", availableFiles.size());
	}

	public void onPlayerEnterMap(int worldId) {
		if (!GeoDataConfig.GEO_NPC_NAVMESH_ENABLE || !availableFiles.containsKey(worldId))
			return;
		int players = playerCounts.computeIfAbsent(worldId, ignored -> new AtomicInteger()).incrementAndGet();
		unloadGenerations.computeIfAbsent(worldId, ignored -> new AtomicInteger()).incrementAndGet();
		ScheduledFuture<?> unloadTask = unloadTasks.remove(worldId);
		if (unloadTask != null)
			unloadTask.cancel(false);
		if (!loadedMeshes.containsKey(worldId))
			loadAsync(worldId);
		if (GeoDataConfig.GEO_NPC_NAVMESH_DEBUG)
			log.info("AL40 world {} now has {} player(s)", worldId, players);
	}

	public void onPlayerLeaveMap(int worldId) {
		if (!GeoDataConfig.GEO_NPC_NAVMESH_ENABLE)
			return;
		AtomicInteger counter = playerCounts.get(worldId);
		if (counter == null)
			return;
		int players = counter.updateAndGet(current -> Math.max(0, current - 1));
		if (players > 0)
			return;

		playerCounts.remove(worldId, counter);
		int generation = unloadGenerations.computeIfAbsent(worldId, ignored -> new AtomicInteger()).incrementAndGet();
		ScheduledFuture<?> previous = unloadTasks.put(worldId,
			ThreadPoolManager.getInstance().schedule(() -> unloadIfInactive(worldId, generation), UNLOAD_DELAY_MS));
		if (previous != null)
			previous.cancel(false);
		if (GeoDataConfig.GEO_NPC_NAVMESH_DEBUG)
			log.info("AL40 world {} has no players; unload scheduled in {} seconds", worldId, UNLOAD_DELAY_MS / 1000);
	}

	private void loadAsync(int worldId) {
		if (failedWorlds.contains(worldId) || !loadingWorlds.add(worldId))
			return;
		ThreadPoolManager.getInstance().executeLongRunning(() -> {
			try {
				Path file = availableFiles.get(worldId);
				if (file == null)
					return;
				long started = System.currentTimeMillis();
				Al40NavMesh mesh;
				// Large worlds are loaded one at a time to avoid temporary heap spikes.
				synchronized (loadLock) {
					if (loadedMeshes.containsKey(worldId))
						return;
					mesh = Al40NavMeshLoader.load(worldId, file);
				}
				AtomicInteger counter = playerCounts.get(worldId);
				if (counter == null || counter.get() <= 0) {
					log.info("Discarded AL40 navmesh {} because its world no longer has players", worldId);
					return;
				}
				loadedMeshes.put(worldId, mesh);
				log.info("Loaded AL40 navmesh {} on demand: {} subgraphs, {} floors, {} MB in {} ms", worldId,
					mesh.subgraphCount(), mesh.floorCount(), Files.size(file) / 1024 / 1024, System.currentTimeMillis() - started);
			} catch (IOException | RuntimeException e) {
				failedWorlds.add(worldId);
				log.error("Could not load navmesh " + availableFiles.get(worldId), e);
			} finally {
				loadingWorlds.remove(worldId);
			}
		});
	}

	private void unloadIfInactive(int worldId, int generation) {
		try {
			AtomicInteger currentGeneration = unloadGenerations.get(worldId);
			if (currentGeneration == null || currentGeneration.get() != generation)
				return;
			AtomicInteger counter = playerCounts.get(worldId);
			if (counter != null && counter.get() > 0)
				return;
			Al40NavMesh removed = loadedMeshes.remove(worldId);
			if (removed != null)
				log.info("Unloaded AL40 navmesh {} after {} seconds without players", worldId, UNLOAD_DELAY_MS / 1000);
		} finally {
			AtomicInteger currentGeneration = unloadGenerations.get(worldId);
			if (currentGeneration != null && currentGeneration.get() == generation)
				unloadTasks.remove(worldId);
		}
	}

	public boolean hasNavMesh(int worldId) {
		return availableFiles.containsKey(worldId) && !failedWorlds.contains(worldId);
	}

	public PathResult findPath(int worldId, Vector3f start, Vector3f goal, int maxVisitedNodes) {
		Al40NavMesh mesh = loadedMeshes.get(worldId);
		if (mesh == null) {
			AtomicInteger counter = playerCounts.get(worldId);
			if (counter != null && counter.get() > 0)
				loadAsync(worldId);
			return PathResult.empty(0);
		}
		return mesh.findPath(start, goal, maxVisitedNodes);
	}

	public boolean contains(int worldId, float x, float y, float z) {
		Al40NavMesh mesh = loadedMeshes.get(worldId);
		return mesh != null && mesh.contains(x, y, z);
	}

	public static NavMeshService getInstance() {
		return Holder.INSTANCE;
	}

	private static final class Holder {
		private static final NavMeshService INSTANCE = new NavMeshService();
	}
}
