package admincommands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.chathandlers.AdminCommand;

/** Lists every spawned object in the current map instance around the administrator. */
public class NearbyIds extends AdminCommand {

	private static final float DEFAULT_RANGE = 30;

	public NearbyIds() {
		super("nearbyids", "Lists nearby objects with their object, template and static IDs.");
		setSyntaxInfo("[range] - Lists every spawned object near you. Default range: 30 meters.");
	}

	@Override
	public void execute(Player admin, String... params) {
		float range = params.length == 0 ? DEFAULT_RANGE : Float.parseFloat(params[0]);
		if (range <= 0)
			throw new IllegalArgumentException("Range must be greater than zero.");

		List<VisibleObject> nearbyObjects = new ArrayList<>();
		admin.getWorldMapInstance().forEachObject(object -> {
			if (!object.equals(admin) && object.isSpawned() && PositionUtil.isInRange(admin, object, range))
				nearbyObjects.add(object);
		});
		nearbyObjects.sort(Comparator.comparingDouble(object -> PositionUtil.getDistance(admin, object)));

		if (nearbyObjects.isEmpty()) {
			sendInfo(admin, "No spawned objects found within " + range + " meters.");
			return;
		}

		StringBuilder result = new StringBuilder("Objects within ").append(range).append(" meters: ");
		for (VisibleObject object : nearbyObjects) {
			int templateId = object.getObjectTemplate() == null ? 0 : object.getObjectTemplate().getTemplateId();
			int staticId = object.getSpawn() == null ? 0 : object.getSpawn().getStaticId();
			result.append("\n").append(object.getClass().getSimpleName())
				.append(" | name=").append(object.getName())
				.append(" | objectId=").append(object.getObjectId())
				.append(" | templateId=").append(templateId)
				.append(" | staticId=").append(staticId)
				.append(" | distance=").append(String.format("%.1f", PositionUtil.getDistance(admin, object)));
		}
		sendInfo(admin, result.toString());
	}
}
