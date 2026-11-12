package codechicken.multipart.handler

import codechicken.lib.CodeChickenLib
import codechicken.multipart.{MultiPartRegistry, TickScheduler, WrappedTileEntityRegistry}
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.event._

@Mod(modid = MultipartMod.modID, dependencies = MultipartMod.deps, acceptedMinecraftVersions = CodeChickenLib.MC_VERSION_DEP, modLanguage = "scala")
object MultipartMod {
    final val modID = "forgemultipartcbe"
    final val deps = "required-after:codechickenlib@[3.3.10,);required-after:forge@[14.23.5.2768,);required-after:scalar@[3.9.0,3.10.0)"

    @EventHandler
    def preInit(event: FMLPreInitializationEvent): Unit = {
        MultipartProxy.preInit(event.getModConfigurationDirectory)
        WrappedTileEntityRegistry.init()
    }

    @EventHandler
    def init(event: FMLInitializationEvent): Unit = {
        MultipartProxy.init()
    }

    @EventHandler
    def postInit(event: FMLPostInitializationEvent): Unit = {
        if (MultiPartRegistry.required) {
            MultiPartRegistry.postInit()
            MultipartProxy.postInit()
        }
    }

    @EventHandler
    def beforeServerStart(event: FMLServerAboutToStartEvent): Unit = {
        TickScheduler.onServerStarting(event.getServer)
        MultiPartRegistry.beforeServerStart()
    }
}
