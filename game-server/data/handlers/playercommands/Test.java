import com.aionemu.gameserver.custom.farmingmap.FarmMapService;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.chathandlers.PlayerCommand;

public class Test extends PlayerCommand {

    public Test() {
        super("test", "teste");
    }

    @Override
    public void execute(Player player, String... params) {
        if (params.length >= 1) {
            if (params[0].equalsIgnoreCase("enter")) {
                FarmMapService.getInstance().joinMap(player);
            } else if (params[0].equalsIgnoreCase("sair")) {
                FarmMapService.getInstance().leaveMap(player);
            } else if (params[0].equalsIgnoreCase("task")) {
							player.getController().cancelTask(TaskId.EXPRESS_MAIL_USE);
						}
        }
    }
	
}