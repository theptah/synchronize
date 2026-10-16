plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1-fabric"

stonecutter parameters {
    val (_, loader) = current.project.split('-', limit = 2)

    properties {
        tags(node.metadata.version, loader)
    }

    constants {
        match(loader, "fabric", "forge", "neoforge")
    }

    replacements {
        string(current.parsed < "1.19.3") {
            replace("net.minecraft.registry.entry.RegistryEntry", "net.minecraft.util.registry.RegistryEntry")
            replace("net.minecraft.registry.DynamicRegistryManager", "net.minecraft.util.registry.DynamicRegistryManager")
            replace("net.minecraft.registry.SimpleRegistry", "net.minecraft.util.registry.SimpleRegistry")
            replace("net.minecraft.registry.RegistryKey", "net.minecraft.util.registry.RegistryKey")
            replace("net.minecraft.registry.Registry", "net.minecraft.util.registry.Registry")
            replace("Lnet/minecraft/registry/", "Lnet/minecraft/util/registry/")
        }

        string(current.parsed < "1.19.3") {
            replace("ButtonWidget.builder(", "net.ptah.synchronize.client.compat.legacy.ButtonBuilder.builder(")
            replace("net.minecraft.client.gui.tooltip.Tooltip", "net.ptah.synchronize.client.compat.legacy.Tooltip")
        }

        string(current.parsed < "1.20") {
            replace("net.minecraft.client.gui.DrawContext", "net.ptah.synchronize.client.compat.legacy.DrawContext")
        }

        string(current.parsed >= "1.20.3") {
            replace("net.minecraft.client.gui.screen.AddServerScreen", "net.minecraft.client.gui.screen.multiplayer.AddServerScreen")
            replace("net.minecraft.client.gui.screen.ConnectScreen", "net.minecraft.client.gui.screen.multiplayer.ConnectScreen")
        }
    }
}
