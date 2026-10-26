package mekanism.common.base;

import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port delta: NeoForge's permission-node API (gatherable nodes, dynamic contexts, offline permission resolution) has no
 * Fabric equivalent, so commands fall back to the vanilla permission levels that the official node resolvers used by default:
 * the root command requires all commands, op commands require game masters, and subcommands inherit their parent level (they are
 * only reached after the parent check). Servers that install a permission plugin lose per-node overrides; that is the recorded
 * platform boundary, and the vanilla level still gates every command exactly like the official fallback path.
 */
public class MekanismPermissions {

    public static final CommandPermissionNode COMMAND = new CommandPermissionNode(Commands.LEVEL_ALL);

    public static final CommandPermissionNode COMMAND_BUILD = nodeOpCommand();
    public static final CommandPermissionNode COMMAND_BUILD_REMOVE = nodeSubCommand(COMMAND_BUILD);

    public static final CommandPermissionNode COMMAND_CHUNK = nodeOpCommand();
    public static final CommandPermissionNode COMMAND_CHUNK_CLEAR = nodeSubCommand(COMMAND_CHUNK);
    public static final CommandPermissionNode COMMAND_CHUNK_FLUSH = nodeSubCommand(COMMAND_CHUNK);
    public static final CommandPermissionNode COMMAND_CHUNK_UNWATCH = nodeSubCommand(COMMAND_CHUNK);
    public static final CommandPermissionNode COMMAND_CHUNK_WATCH = nodeSubCommand(COMMAND_CHUNK);

    public static final CommandPermissionNode COMMAND_DEBUG = nodeOpCommand();

    public static final CommandPermissionNode COMMAND_FORCE_RETROGEN = nodeOpCommand();

    public static final CommandPermissionNode COMMAND_RADIATION = nodeOpCommand();
    public static final CommandPermissionNode COMMAND_RADIATION_ADD = nodeSubCommand(COMMAND_RADIATION);
    public static final CommandPermissionNode COMMAND_RADIATION_ADD_ENTITY = nodeSubCommand(COMMAND_RADIATION_ADD);
    public static final CommandPermissionNode COMMAND_RADIATION_ADD_ENTITY_OTHERS = nodeSubCommand(COMMAND_RADIATION_ADD_ENTITY);
    public static final CommandPermissionNode COMMAND_RADIATION_GET = nodeSubCommand(COMMAND_RADIATION);
    public static final CommandPermissionNode COMMAND_RADIATION_HEAL = nodeSubCommand(COMMAND_RADIATION);
    public static final CommandPermissionNode COMMAND_RADIATION_HEAL_OTHERS = nodeSubCommand(COMMAND_RADIATION_HEAL);
    public static final CommandPermissionNode COMMAND_RADIATION_REDUCE = nodeSubCommand(COMMAND_RADIATION);
    public static final CommandPermissionNode COMMAND_RADIATION_REDUCE_OTHERS = nodeSubCommand(COMMAND_RADIATION_REDUCE);
    public static final CommandPermissionNode COMMAND_RADIATION_REMOVE_ALL = nodeSubCommand(COMMAND_RADIATION);

    public static final CommandPermissionNode COMMAND_TEST_RULES = nodeOpCommand();
    public static final CommandPermissionNode COMMAND_TP = nodeOpCommand();
    public static final CommandPermissionNode COMMAND_TP_POP = nodeSubCommand(COMMAND_TP);

    private static CommandPermissionNode nodeOpCommand() {
        return new CommandPermissionNode(Commands.LEVEL_GAMEMASTERS);
    }

    private static CommandPermissionNode nodeSubCommand(CommandPermissionNode parent) {
        //Sub commands can assume the parent was checked, so they inherit its fallback level
        return new CommandPermissionNode(parent.fallbackLevel);
    }

    /**
     * Official {@code BYPASS_SECURITY} default resolver: the operator list, not merely permission level.
     */
    public static boolean bypassSecurity(@Nullable ServerPlayer player) {
        return player != null && player.server.getPlayerList().isOp(player.getGameProfile());
    }

    public record CommandPermissionNode(int fallbackLevel) implements Predicate<CommandSourceStack> {

        @Override
        public boolean test(CommandSourceStack source) {
            return source.hasPermission(fallbackLevel);
        }
    }
}
