package mekanism.client.render.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.common.collect.Table;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Predicate;
import mekanism.client.model.MekanismModelCache;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

class MekaSuitModuleModelClientTest {

    @Test
    void registeredModelHasClassificationOwnerAndDeduplicates() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var cache = MekanismModelCache.INSTANCE;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", "models/entity/test_module.obj");
        var model = cache.registerMekaSuitModuleModel(id);
        assertSame(model, cache.registerMekaSuitModuleModel(id));
        assertEquals(MekaSuitArmor.ModuleOBJModelData.class, model.getClass());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void classificationFollowsChangedGroupsAfterReload() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Class<?> specClass = Class.forName("mekanism.client.render.armor.MekaSuitArmor$ModuleModelSpec");
        Constructor<?> constructor = specClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object spec = constructor.newInstance(null, EquipmentSlot.CHEST, "jetpack", (Predicate<LivingEntity>) entity -> true);
        Field tableField = MekaSuitArmor.class.getDeclaredField("moduleModelSpec");
        tableField.setAccessible(true);
        Table table = (Table) tableField.get(null);
        Object key = Holder.direct(new Object());
        table.put(EquipmentSlot.CHEST, key, spec);
        try {
            class Model extends MekaSuitArmor.ModuleOBJModelData {
                Set<String> groups = Set.of("jetpack_chest_old", "inactive_jetpack_chest_old");
                Model() { super(ResourceLocation.fromNamespaceAndPath("mekanism", "models/entity/test.obj")); }
                @Override public Set<String> getRootComponentNames() { return groups; }
            }
            Model model = new Model();
            Method parts = MekaSuitArmor.ModuleOBJModelData.class.getDeclaredMethod("getPartsForSpec", specClass, boolean.class);
            parts.setAccessible(true);
            assertEquals(Set.of("jetpack_chest_old"), parts.invoke(model, spec, true));
            assertEquals(Set.of("inactive_jetpack_chest_old"), parts.invoke(model, spec, false));
            model.groups = Set.of("jetpack_chest_new");
            model.clearCached();
            assertEquals(Set.of("jetpack_chest_new"), parts.invoke(model, spec, true));
            assertEquals(Set.of(), parts.invoke(model, spec, false));
        } finally {
            table.remove(EquipmentSlot.CHEST, key);
        }
    }
}
