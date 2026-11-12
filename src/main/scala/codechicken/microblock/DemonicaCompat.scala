package codechicken.microblock

import java.lang.reflect.Method
import net.minecraft.block.Block
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

/** Optional Demonica 0.9 material IDs, captured per quad by its BufferBuilder hook. */
@SideOnly(Side.CLIENT)
object DemonicaCompat {
    private case class Backend(setMaterial: Method, getId: Method, setId: Method)

    private lazy val backend: Option[Backend] = {
        if (!Loader.isModLoaded("demonica")) None
        else {
            try {
                val loader = getClass.getClassLoader
                val iris = Class.forName("net.coderbot.iris.Iris", false, loader)
                val overrides = Class.forName("net.coderbot.iris.celeritas.buffer.ShaderMaterialOverrideState", false, loader)
                Some(Backend(iris.getMethod("setShaderMaterialOverride", classOf[Block], Integer.TYPE),
                    overrides.getMethod("getBlockId"), overrides.getMethod("setBlockId", Integer.TYPE)))
            } catch {
                case error: ReflectiveOperationException =>
                    logger.warn("Demonica material override API is unavailable; using ordinary multipart rendering", error)
                    None
                case error: LinkageError =>
                    logger.warn("Demonica material override API cannot be linked; using ordinary multipart rendering", error)
                    None
            }
        }
    }

    def withMaterial[T](material: IMicroMaterial, worldRendering: Boolean)(draw: => T): T = {
        (if (worldRendering) backend else None, material) match {
            case (Some(api), block: BlockMicroMaterial) =>
                val previous = api.getId.invoke(null).asInstanceOf[Integer].intValue()
                try {
                    api.setMaterial.invoke(null, block.state.getBlock,
                        Int.box(block.state.getBlock.getMetaFromState(block.state)))
                    draw
                } finally {
                    // Preserve an enclosing render's override, including when draw throws.
                    api.setId.invoke(null, Int.box(previous))
                }
            case _ => draw
        }
    }
}
