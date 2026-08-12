package com.aionemu.gameserver.custom.farmingmap;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.world.WorldMapInstance;

public class FarmMapService {
	
	private static final FarmMapService instance = new FarmMapService();
	private FarmMapHandler handler;
	
	public static FarmMapService getInstance() { return instance; }
	
	public void init() {
		WorldMapInstance instance = InstanceService.getNextAvailableInstance(300040000, 0, (byte) 0, FarmMapHandler::new, 0, false);
		handler = (FarmMapHandler) instance.getInstanceHandler();
	}
	
	public void onLogin(Player player) {
		// nothing now
	}
	
	public void joinMap(Player p) {
		if (handler != null && !handler.isOnMap(p))
			handler.join(p);
	}
	
	public void leaveMap(Player p) {
		if (handler != null && handler.isOnMap(p))
			handler.leave(p);
	}
	
	public boolean isOnFarmMap(Creature creature) { return handler != null && handler.isOnMap(creature); }
	
	public int getParticipantsSize() { return handler == null ? 0 : handler.getParticipantsSize(); }
	
	void onInstanceDestroy() { handler = null; }
	
}
