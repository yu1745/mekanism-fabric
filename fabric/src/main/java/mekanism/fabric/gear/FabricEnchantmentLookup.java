package mekanism.fabric.gear;

import mekanism.api.MekanismServerLifecycle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;

/** Resolve the same live server-first enchantment lookup used for gameplay-only enchantments. */
public final class FabricEnchantmentLookup {
    private FabricEnchantmentLookup() {
    }

    @Nullable
    public static Holder<Enchantment> flame(RegistryAccess access) {
        return access.lookup(Registries.ENCHANTMENT).flatMap(registry -> registry.get(Enchantments.FLAME)).orElse(null);
    }

    @Nullable
    public static Holder<Enchantment> currentFlame() {
        MinecraftServer server = MekanismServerLifecycle.getServer();
        if (server != null) return flame(server.registryAccess());
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? ClientAccess.flame() : null;
    }

    @Environment(EnvType.CLIENT)
    private static final class ClientAccess {
        @Nullable
        private static Holder<Enchantment> flame() {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? null : FabricEnchantmentLookup.flame(level.registryAccess());
        }
    }
}
