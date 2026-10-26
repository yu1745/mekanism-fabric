package mekanism.common.base;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Single shared level accessor for the Mekanism contracts implemented by block entities.
 *
 * <p>Fabric port delta: a mod interface cannot redeclare a mapped vanilla method name as abstract, because the interface itself is not in a Minecraft supertype chain
 * and the remapper therefore keeps {@code getLevel} while {@code BlockEntity#getLevel} is renamed; the result was an {@link AbstractMethodError} on the first machine
 * tick. Two unrelated defaults for the same signature also fail to resolve, so every such contract extends this one instead of declaring its own.
 */
public interface ILevelProvider {

    @Nullable
    default Level getLevel() {
        return ((BlockEntity) this).getLevel();
    }
}
