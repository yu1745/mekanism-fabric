package mekanism.api;

import java.util.Iterator;
import java.util.ServiceLoader;

/** Deterministic owner for Mekanism public service discovery. */
public final class MekanismServices {
    private MekanismServices() {
    }

    public static <T> T load(Class<T> service) {
        Iterator<T> providers = ServiceLoader.load(service, service.getClassLoader()).iterator();
        if (!providers.hasNext()) {
            throw new IllegalStateException("No provider found for Mekanism service " + service.getName());
        }
        T provider = providers.next();
        if (providers.hasNext()) {
            throw new IllegalStateException("Multiple providers found for Mekanism service " + service.getName());
        }
        return provider;
    }
}
