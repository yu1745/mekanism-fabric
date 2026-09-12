package mekanism.common.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import mekanism.common.registration.impl.DamageTypeRegister;
import mekanism.common.registration.impl.MekanismDamageType;
import mekanism.common.registration.impl.SoundEventDeferredRegister;
import mekanism.common.registration.impl.SoundEventRegistryObject;
import mekanism.common.registries.MekanismDamageTypes;
import mekanism.common.registries.MekanismSounds;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DeathMessageType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SoundAndDamageRegistrationTest {

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void freshDefaultSoundRegistrationIsLazySpecializedVariableRangeAndCanonicallyBound() {
        SoundEventDeferredRegister register = new SoundEventDeferredRegister("sound_default_test");
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath("sound_default_test", "variable");
        ResourceKey<SoundEvent> expectedKey = ResourceKey.create(Registries.SOUND_EVENT, expectedId);
        AtomicInteger registrarCalls = new AtomicInteger();
        AtomicReference<SoundEvent> created = new AtomicReference<>();
        SoundEvent canonical = SoundEvent.createVariableRangeEvent(expectedId);

        SoundEventRegistryObject<SoundEvent> holder = register.register("variable");

        assertInstanceOf(SoundEventRegistryObject.class, holder);
        assertEquals(expectedId, holder.getId());
        assertEquals(expectedKey, holder.getKey());
        assertEquals("sound_event.sound_default_test.variable", holder.getTranslationKey());
        assertFalse(holder.isBound());
        assertTrue(holder.asOptional().isEmpty());
        assertNull(created.get());
        assertThrows(IllegalStateException.class, holder::value);

        ((MekanismDeferredRegister<SoundEvent>) register).registerAll((registryKey, key, value) -> {
            registrarCalls.incrementAndGet();
            assertEquals(Registries.SOUND_EVENT, registryKey);
            assertEquals(expectedKey, key);
            assertEquals(expectedId, value.getLocation());
            created.set(value);
            return canonical;
        });

        assertEquals(1, registrarCalls.get());
        assertNotSame(canonical, created.get());
        assertEquals(16F, created.get().getRange(0.5F));
        assertEquals(32F, created.get().getRange(2F));
        assertTrue(holder.isBound());
        assertSame(canonical, holder.get());
        assertSame(canonical, holder.value());
    }

    @Test
    void freshSoundFunctionAndSupplierOverloadsPreserveIdsLazinessIdentityAndLifecycleRules() {
        SoundEventDeferredRegister register = new SoundEventDeferredRegister("sound_overload_test");
        ResourceLocation fixedId = ResourceLocation.fromNamespaceAndPath("sound_overload_test", "fixed");
        ResourceLocation suppliedId = ResourceLocation.fromNamespaceAndPath("sound_overload_test", "supplied");
        AtomicInteger functionCalls = new AtomicInteger();
        AtomicInteger supplierCalls = new AtomicInteger();
        AtomicInteger duplicateCalls = new AtomicInteger();
        AtomicInteger lateCalls = new AtomicInteger();
        AtomicReference<ResourceLocation> forwardedId = new AtomicReference<>();
        AtomicReference<SoundEvent> fixedCreated = new AtomicReference<>();
        SoundEvent supplied = SoundEvent.createVariableRangeEvent(suppliedId);

        SoundEventRegistryObject<SoundEvent> fixed = register.register("fixed", id -> {
            functionCalls.incrementAndGet();
            forwardedId.set(id);
            SoundEvent value = SoundEvent.createFixedRangeEvent(id, 23.5F);
            fixedCreated.set(value);
            return value;
        });
        SoundEventRegistryObject<SoundEvent> variable = register.register("supplied", () -> {
            supplierCalls.incrementAndGet();
            return supplied;
        });

        assertEquals(fixedId, fixed.getId());
        assertEquals(suppliedId, variable.getId());
        assertEquals(0, functionCalls.get());
        assertEquals(0, supplierCalls.get());
        assertFalse(fixed.isBound());
        assertFalse(variable.isBound());
        assertThrows(IllegalArgumentException.class, () -> register.register("fixed", () -> {
            duplicateCalls.incrementAndGet();
            return SoundEvent.createVariableRangeEvent(fixedId);
        }));
        assertEquals(0, duplicateCalls.get());

        Map<ResourceKey<SoundEvent>, SoundEvent> committed = new HashMap<>();
        ((MekanismDeferredRegister<SoundEvent>) register).registerAll((registryKey, key, value) -> {
            assertEquals(Registries.SOUND_EVENT, registryKey);
            committed.put(key, value);
            return value;
        });

        assertEquals(1, functionCalls.get());
        assertEquals(1, supplierCalls.get());
        assertEquals(fixedId, forwardedId.get());
        assertSame(fixedCreated.get(), fixed.value());
        assertSame(supplied, variable.value());
        assertSame(fixed.value(), committed.get(fixed.getKey()));
        assertSame(variable.value(), committed.get(variable.getKey()));
        assertEquals(23.5F, fixed.value().getRange(0.5F));
        assertEquals(23.5F, fixed.value().getRange(2F));
        assertEquals(16F, variable.value().getRange(0.5F));
        assertEquals(32F, variable.value().getRange(2F));
        assertThrows(IllegalStateException.class, () -> register.register("late", () -> {
            lateCalls.incrementAndGet();
            return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("sound_overload_test", "late"));
        }));
        assertEquals(0, lateCalls.get());
        assertThrows(IllegalStateException.class,
              () -> ((MekanismDeferredRegister<SoundEvent>) register).registerAll((registryKey, key, value) -> value));
    }

    @Test
    void mekanismSoundsHasTheCompleteFortySevenDeclarationTableWithStableUnboundHolders() throws ReflectiveOperationException {
        Map<String, String> expectedDeclarations = new LinkedHashMap<>();
        expectedDeclarations.put("SOUND_EVENTS", null);
        expectedDeclarations.put("CHARGEPAD", "tile.machine.chargepad");
        expectedDeclarations.put("CHEMICAL_CRYSTALLIZER", "tile.machine.chemical_crystallizer");
        expectedDeclarations.put("CHEMICAL_DISSOLUTION_CHAMBER", "tile.machine.chemical_dissolution_chamber");
        expectedDeclarations.put("CHEMICAL_INFUSER", "tile.machine.chemical_infuser");
        expectedDeclarations.put("CHEMICAL_INJECTION_CHAMBER", "tile.machine.chemical_injection_chamber");
        expectedDeclarations.put("CHEMICAL_OXIDIZER", "tile.machine.chemical_oxidizer");
        expectedDeclarations.put("CHEMICAL_WASHER", "tile.machine.chemical_washer");
        expectedDeclarations.put("COMBINER", "tile.machine.combiner");
        expectedDeclarations.put("OSMIUM_COMPRESSOR", "tile.machine.compressor");
        expectedDeclarations.put("CRUSHER", "tile.machine.crusher");
        expectedDeclarations.put("ELECTROLYTIC_SEPARATOR", "tile.machine.electrolytic_separator");
        expectedDeclarations.put("ENRICHMENT_CHAMBER", "tile.machine.enrichment_chamber");
        expectedDeclarations.put("LASER", "tile.machine.laser");
        expectedDeclarations.put("LOGISTICAL_SORTER", "tile.machine.logistical_sorter");
        expectedDeclarations.put("METALLURGIC_INFUSER", "tile.machine.metallurgic_infuser");
        expectedDeclarations.put("PRECISION_SAWMILL", "tile.machine.precision_sawmill");
        expectedDeclarations.put("PRESSURIZED_REACTION_CHAMBER", "tile.machine.pressurized_reaction_chamber");
        expectedDeclarations.put("PURIFICATION_CHAMBER", "tile.machine.purification_chamber");
        expectedDeclarations.put("RESISTIVE_HEATER", "tile.machine.resistive_heater");
        expectedDeclarations.put("ROTARY_CONDENSENTRATOR", "tile.machine.rotary_condensentrator");
        expectedDeclarations.put("ENERGIZED_SMELTER", "tile.machine.energized_smelter");
        expectedDeclarations.put("ISOTOPIC_CENTRIFUGE", "tile.machine.isotopic_centrifuge");
        expectedDeclarations.put("NUTRITIONAL_LIQUIFIER", "tile.machine.nutritional_liquifier");
        expectedDeclarations.put("INDUSTRIAL_ALARM", "tile.machine.industrial_alarm");
        expectedDeclarations.put("ANTIPROTONIC_NUCLEOSYNTHESIZER", "tile.machine.antiprotonic_nucleosynthesizer");
        expectedDeclarations.put("PIGMENT_EXTRACTOR", "tile.machine.pigment_extractor");
        expectedDeclarations.put("PIGMENT_MIXER", "tile.machine.pigment_mixer");
        expectedDeclarations.put("PAINTING_MACHINE", "tile.machine.painting_machine");
        expectedDeclarations.put("SPS", "tile.machine.sps");
        expectedDeclarations.put("FLAMETHROWER_IDLE", "item.flamethrower.idle");
        expectedDeclarations.put("FLAMETHROWER_ACTIVE", "item.flamethrower.active");
        expectedDeclarations.put("SCUBA_MASK", "item.scuba_mask");
        expectedDeclarations.put("JETPACK", "item.jetpack");
        expectedDeclarations.put("HYDRAULIC", "item.hydraulic");
        expectedDeclarations.put("GRAVITATIONAL_MODULATION_UNIT", "item.gravitational_modulation_unit");
        expectedDeclarations.put("GEIGER_SLOW", "item.geiger_slow");
        expectedDeclarations.put("GEIGER_MEDIUM", "item.geiger_medium");
        expectedDeclarations.put("GEIGER_ELEVATED", "item.geiger_elevated");
        expectedDeclarations.put("GEIGER_FAST", "item.geiger_fast");
        expectedDeclarations.put("BEEP_ON", "gui.digital_beep_on");
        expectedDeclarations.put("BEEP_OFF", "gui.digital_beep_off");
        expectedDeclarations.put("CHRISTMAS1", "tile.christmas.1");
        expectedDeclarations.put("CHRISTMAS2", "tile.christmas.2");
        expectedDeclarations.put("CHRISTMAS3", "tile.christmas.3");
        expectedDeclarations.put("CHRISTMAS4", "tile.christmas.4");
        expectedDeclarations.put("CHRISTMAS5", "tile.christmas.5");

        Set<String> actualPublicNames = Arrays.stream(MekanismSounds.class.getDeclaredFields())
              .filter(field -> Modifier.isPublic(field.getModifiers()))
              .map(Field::getName)
              .collect(Collectors.toSet());
        assertEquals(47, expectedDeclarations.size());
        assertEquals(expectedDeclarations.keySet(), actualPublicNames);
        assertEquals(47, actualPublicNames.size());
        assertSame(MekanismSounds.SOUND_EVENTS, MekanismSounds.class.getField("SOUND_EVENTS").get(null));

        int holderCount = 0;
        for (Map.Entry<String, String> declaration : expectedDeclarations.entrySet()) {
            Field field = MekanismSounds.class.getField(declaration.getKey());
            assertTrue(Modifier.isStatic(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            if (declaration.getValue() == null) {
                assertEquals(SoundEventDeferredRegister.class, field.getType());
                continue;
            }
            holderCount++;
            SoundEventRegistryObject<?> holder = assertInstanceOf(SoundEventRegistryObject.class, field.get(null));
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", declaration.getValue());
            assertEquals(id, holder.getId());
            assertEquals(ResourceKey.create(Registries.SOUND_EVENT, id), holder.getKey());
            assertEquals("sound_event.mekanism." + declaration.getValue().replace('/', '.'), holder.getTranslationKey());
            assertFalse(holder.isBound());
            assertTrue(holder.asOptional().isEmpty());
        }
        assertEquals(46, holderCount);
    }

    @Test
    void damageRegisterAndVanillaHolderExpansionPreserveAllOverloadsViewsAndSources() {
        DamageTypeRegister register = new DamageTypeRegister("damage_test");
        Collection<MekanismDamageType> liveTypes = register.damageTypes();
        assertTrue(liveTypes.isEmpty());
        assertThrows(UnsupportedOperationException.class,
              () -> liveTypes.add(new MekanismDamageType(ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("damage_test", "forbidden")), 0, DamageEffects.HURT)));

        MekanismDamageType defaultType = register.create("default");
        assertEquals(1, liveTypes.size());
        MekanismDamageType exhaustionType = register.create("exhaustion", 0.25F);
        MekanismDamageType effectsType = register.create("effects", 0.75F, DamageEffects.DROWNING);
        assertDamageMetadata(defaultType, "damage_test", "default", 0, DamageEffects.HURT);
        assertDamageMetadata(exhaustionType, "damage_test", "exhaustion", 0.25F, DamageEffects.HURT);
        assertDamageMetadata(effectsType, "damage_test", "effects", 0.75F, DamageEffects.DROWNING);

        MekanismDamageType firstDuplicate = register.create("duplicate", 0.5F, DamageEffects.THORNS);
        int sizeBeforeReplacement = liveTypes.size();
        MekanismDamageType replacement = register.create("duplicate", 1.5F, DamageEffects.FREEZING);
        assertEquals(sizeBeforeReplacement, liveTypes.size());
        assertFalse(liveTypes.stream().anyMatch(type -> type == firstDuplicate));
        assertEquals(1, liveTypes.stream().filter(type -> type == replacement).count());
        assertDamageMetadata(replacement, "damage_test", "duplicate", 1.5F, DamageEffects.FREEZING);

        MekanismDamageType lookupType = register.create("lookup", 0.625F, DamageEffects.POKING);
        assertEquals(5, liveTypes.size());
        assertDamageMetadata(lookupType, "damage_test", "lookup", 0.625F, DamageEffects.POKING);
        MappedRegistry<DamageType> localRegistry = new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        DamageType canonical = lookupType.toVanilla();
        Holder.Reference<DamageType> registeredHolder = localRegistry.register(lookupType.key(), canonical, RegistrationInfo.BUILT_IN);
        localRegistry.freeze();
        RegistryAccess localAccess = new RegistryAccess.ImmutableRegistryAccess(List.of(localRegistry));

        DamageSource plain = lookupType.source(localAccess);
        assertSame(registeredHolder, plain.typeHolder());
        assertSame(canonical, plain.type());
        assertNull(plain.sourcePositionRaw());
        assertTrue(lookupType.is(plain));

        Vec3 position = new Vec3(1.25, -2.5, 3.75);
        DamageSource positioned = lookupType.source(localAccess, position);
        assertSame(registeredHolder, positioned.typeHolder());
        assertSame(canonical, positioned.type());
        assertSame(position, positioned.sourcePositionRaw());
        assertSame(position, positioned.getSourcePosition());
        assertTrue(lookupType.is(positioned));

        DamageSource nullableEntities = lookupType.source(localAccess, null, null);
        assertSame(registeredHolder, nullableEntities.typeHolder());
        assertSame(canonical, nullableEntities.type());
        assertNull(nullableEntities.getDirectEntity());
        assertNull(nullableEntities.getEntity());
        assertNull(nullableEntities.sourcePositionRaw());
        assertTrue(lookupType.is(nullableEntities));

        MekanismDamageType missingKey = new MekanismDamageType(ResourceKey.create(Registries.DAMAGE_TYPE,
              ResourceLocation.fromNamespaceAndPath("damage_test", "missing")), 0, DamageEffects.HURT);
        assertThrows(IllegalStateException.class, () -> missingKey.source(localAccess));
        assertThrows(IllegalStateException.class, () -> lookupType.source(RegistryAccess.EMPTY));
    }

    @Test
    void mekanismDamageTypesHasTheCompleteFourEntryLiveIdentityAndMetadataTable() {
        Map<String, MekanismDamageType> expected = Map.of(
              "flamethrower", MekanismDamageTypes.FLAMETHROWER,
              "laser", MekanismDamageTypes.LASER,
              "radiation", MekanismDamageTypes.RADIATION,
              "sps", MekanismDamageTypes.SPS
        );
        Map<String, Float> exhaustion = Map.of(
              "flamethrower", 0.1F,
              "laser", 0.1F,
              "radiation", 0F,
              "sps", 0F
        );
        Map<String, DamageEffects> effects = Map.of(
              "flamethrower", DamageEffects.BURNING,
              "laser", DamageEffects.HURT,
              "radiation", DamageEffects.HURT,
              "sps", DamageEffects.HURT
        );
        Collection<MekanismDamageType> liveTypes = MekanismDamageTypes.DAMAGE_TYPES.damageTypes();

        assertEquals(4, expected.size());
        assertEquals(4, liveTypes.size());
        for (Map.Entry<String, MekanismDamageType> declaration : expected.entrySet()) {
            MekanismDamageType type = declaration.getValue();
            assertEquals(1, liveTypes.stream().filter(actual -> actual == type).count());
            assertDamageMetadata(type, "mekanism", declaration.getKey(), exhaustion.get(declaration.getKey()), effects.get(declaration.getKey()));
        }
        assertTrue(liveTypes.stream().allMatch(actual -> expected.values().stream().anyMatch(type -> type == actual)));
    }

    private static void assertDamageMetadata(MekanismDamageType type, String namespace, String path, float exhaustion, DamageEffects effects) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        assertEquals(ResourceKey.create(Registries.DAMAGE_TYPE, id), type.key());
        assertEquals(id, type.registryName());
        assertEquals(exhaustion, type.exhaustion());
        assertSame(effects, type.effects());
        assertEquals("death.attack." + namespace + "." + path.replace('/', '.'), type.getTranslationKey());

        DamageType vanilla = type.toVanilla();
        assertEquals(namespace + "." + path.replace('/', '.'), vanilla.msgId());
        assertEquals(exhaustion, vanilla.exhaustion());
        assertSame(effects, vanilla.effects());
        assertSame(DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, vanilla.scaling());
        assertSame(DeathMessageType.DEFAULT, vanilla.deathMessageType());
    }
}
