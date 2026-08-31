package net.krodark.asterion.dev;

/** Runs after Loom's dev-launch injector, which otherwise overwrites the VM property. */
public final class EssentialClientLaunch {
    private EssentialClientLaunch() { }
    public static void main(String[] args) throws Exception {
        // Production Fabric does not discover mod dependencies from Loom's classpath.
        // Supply their real jars explicitly, plus the freshly built Asterion jar.
        var mods = new java.util.LinkedHashSet<String>();
        String existing = System.getProperty("fabric.addMods", "");
        if (!existing.isEmpty()) mods.add(existing);
        mods.add(System.getProperty("asterion.essential.modJar"));
        var metadata = ClassLoader.getSystemResources("fabric.mod.json");
        while (metadata.hasMoreElements()) {
            var url = metadata.nextElement();
            if (url.openConnection() instanceof java.net.JarURLConnection jar)
                mods.add(java.nio.file.Path.of(jar.getJarFileURL().toURI()).toString());
        }
        System.setProperty("fabric.addMods", String.join(java.io.File.pathSeparator, mods));
        System.setProperty("fabric.development", "false");
        net.fabricmc.loader.impl.launch.knot.KnotClient.main(args);
    }
}
