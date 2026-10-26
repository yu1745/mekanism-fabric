package mekanism.common.registration.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.MekanismDeferredRegister;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric port delta: the official NeoForge mod-bus listeners for entity attribute creation and spawn placements become part of
 * {@link #registerAll(Registry)}. Fabric registers attributes through {@link FabricDefaultAttributeRegistry} and placements
 * through the vanilla {@link SpawnPlacements} table, which is what the NeoForge events ultimately wrote to; the official AND
 * operation collapsed because these registrations are the first (and only) placement entry for their types.
 */
public class EntityTypeDeferredRegister extends MekanismDeferredRegister<EntityType<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism");

    private Map<Supplier<? extends EntityType<? extends LivingEntity>>, Supplier<AttributeSupplier.Builder>> livingEntityAttributes = new HashMap<>();
    private Map<Supplier<? extends EntityType<? extends LivingEntity>>, SpawnPlacementData<?>> livingEntityPlacements = new HashMap<>();

    public EntityTypeDeferredRegister(String modid) {
        super(Registries.ENTITY_TYPE, modid);
    }

    public <ENTITY extends Monster> MekanismDeferredHolder<EntityType<?>, EntityType<ENTITY>> registerBasicMonster(String name,
          Supplier<EntityType.Builder<ENTITY>> builder, Supplier<AttributeSupplier.Builder> attributes) {
        return registerBasicPlacement(name, builder, attributes, Monster::checkMonsterSpawnRules);
    }

    public <ENTITY extends LivingEntity> MekanismDeferredHolder<EntityType<?>, EntityType<ENTITY>> registerBasicPlacement(String name,
          Supplier<EntityType.Builder<ENTITY>> builder, Supplier<AttributeSupplier.Builder> attributes, SpawnPlacements.SpawnPredicate<ENTITY> placementPredicate) {
        return register(name, builder, attributes, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, placementPredicate);
    }

    public <ENTITY extends LivingEntity> MekanismDeferredHolder<EntityType<?>, EntityType<ENTITY>> register(String name, Supplier<EntityType.Builder<ENTITY>> builder,
          Supplier<AttributeSupplier.Builder> attributes, @Nullable SpawnPlacementType placementType, @Nullable Heightmap.Types heightmap,
          SpawnPlacements.SpawnPredicate<ENTITY> placementPredicate) {
        MekanismDeferredHolder<EntityType<?>, EntityType<ENTITY>> entityTypeRO = register(name, builder, attributes);
        livingEntityPlacements.put(entityTypeRO, new SpawnPlacementData<>(placementType, heightmap, placementPredicate));
        return entityTypeRO;
    }

    public <ENTITY extends LivingEntity> MekanismDeferredHolder<EntityType<?>, EntityType<ENTITY>> register(String name, Supplier<EntityType.Builder<ENTITY>> builder,
          Supplier<AttributeSupplier.Builder> attributes) {
        MekanismDeferredHolder<EntityType<?>, EntityType<ENTITY>> entityTypeRO = registerBuilder(name, builder);
        livingEntityAttributes.put(entityTypeRO, attributes);
        return entityTypeRO;
    }

    public <ENTITY extends Entity> MekanismDeferredHolder<EntityType<?>, EntityType<ENTITY>> registerBuilder(String name, Supplier<EntityType.Builder<ENTITY>> builder) {
        return register(name, () -> builder.get().build(name));
    }

    @Override
    public void registerAll(@NotNull Registry<EntityType<?>> registry) {
        super.registerAll(registry);
        registerEntityAttributes();
        registerPlacements();
    }

    private void registerEntityAttributes() {
        if (livingEntityAttributes == null) {
            LOGGER.error("Entity Attributes have already been created. This should not happen.");
        } else {
            //Register our living entity attributes
            for (Map.Entry<Supplier<? extends EntityType<? extends LivingEntity>>, Supplier<AttributeSupplier.Builder>> entry : livingEntityAttributes.entrySet()) {
                FabricDefaultAttributeRegistry.register(entry.getKey().get(), entry.getValue().get().build());
            }
            //And set the map to null to allow it to be garbage collected
            livingEntityAttributes = null;
        }
    }

    private void registerPlacements() {
        if (livingEntityPlacements == null) {
            LOGGER.error("Entity Placements have already been set. This should not happen.");
        } else {
            //Register our living entity placements
            for (Map.Entry<Supplier<? extends EntityType<? extends LivingEntity>>, SpawnPlacementData<?>> entry : livingEntityPlacements.entrySet()) {
                entry.getValue().register(entry.getKey().get());
            }
            //And set the map to null to allow it to be garbage collected
            livingEntityPlacements = null;
        }
    }

    private record SpawnPlacementData<ENTITY extends LivingEntity>(@Nullable SpawnPlacementType placementType, @Nullable Heightmap.Types heightmap,
                                                                   SpawnPlacements.SpawnPredicate<ENTITY> predicate) {

        @SuppressWarnings("unchecked")
        private void register(EntityType<?> entityType) {
            if (placementType == null && heightmap == null) {
                //Nothing was specified to register; keep the vanilla default rather than inventing a placement
                return;
            }
            SpawnPlacements.register((EntityType<ENTITY>) entityType, placementType, heightmap, predicate);
        }
    }
}
