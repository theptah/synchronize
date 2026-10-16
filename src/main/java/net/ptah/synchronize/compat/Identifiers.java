package net.ptah.synchronize.compat;

import net.minecraft.util.Identifier;

 




public final class Identifiers {
    private Identifiers() {
    }

    public static Identifier mod(String path) {
        return of("synchronize", path);
    }

    public static Identifier of(String namespace, String path) {
        //? if >=1.21 {
        /*return Identifier.of(namespace, path);
        *///?} else
        return new Identifier(namespace, path);
    }

    public static Identifier parse(String id) {
        //? if >=1.21 {
        /*return Identifier.of(id);
        *///?} else
        return new Identifier(id);
    }
}
