package codechicken.microblock

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.packet.PacketCustom
import codechicken.lib.render.pipeline.IVertexOperation
import codechicken.lib.vec.{Cuboid6, Vector3}
import codechicken.multipart.{IDWriter, MultiPartRegistry}
import net.minecraft.block.SoundType
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.util.BlockRenderLayer
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.jdk.CollectionConverters._
import scala.collection.mutable.{ListBuffer, HashMap => MHashMap}

/**
 * Interface for defining a micro material
 */
trait IMicroMaterial extends Ordered[IMicroMaterial] {
    /**
     * The icon to be used for breaking particles on side
     */
    @SideOnly(Side.CLIENT)
    def getBreakingIcon(side: Int): TextureAtlasSprite

    /**
     * Callback to load icons from the underlying block/etc
     */
    @SideOnly(Side.CLIENT)
    def loadIcons(): Unit = {}

    /**
     * This function must return a list of vertex operations, one set for each
     * rendering pass on the preloaded model.
     *
     * @param pos    The current position to render the cuboid
     * @param side   The side that is being rendered as EnumFacing indexes
     * @param layer  The current render layer, null for inventory rendering
     * @param bounds The cuboid bounds of the face being rendered
     */
    @SideOnly(Side.CLIENT)
    def getMicroRenderOps(pos: Vector3, side: Int, layer: BlockRenderLayer, bounds: Cuboid6): Seq[Seq[IVertexOperation]]

    /**
     * Get the render pass for which this material renders in.
     */
    def canRenderInLayer(layer: BlockRenderLayer) = layer == BlockRenderLayer.SOLID

    /**
     * Return true if this material is not opaque (glass, ice).
     */
    def isTransparent: Boolean

    /**
     * Return the light level emitted by this material (glowstone)
     */
    def getLightValue: Int

    /**
     * Return the strength of this material
     */
    def getStrength(player: EntityPlayer): Float

    /**
     * Return the localised name of this material (normally the block name)
     */
    def getLocalizedName: String

    /**
     * Get the item that this material is cut from (full block -> slabs)
     */
    def getItem: ItemStack

    /**
     * Get the strength of saw requried to cut this material
     */
    def getCutterStrength: Int

    /**
     * Get the breaking/walking sound
     */
    def getSound: SoundType

    /**
     * Return true if this material is solid and opaque (can run wires on etc)
     */
    def isSolid = !isTransparent

    /**
     * Get the explosion resistance of this part to an explosion caused by entity
     */
    def explosionResistance(entity: Entity): Float

    def getMaterialID: String
}

/**
 * Interface for overriding the default micro placement highlight handler to show the effect of placement on a certain block/part
 */
trait IMicroHighlightRenderer {
    /**
     * Return true if a custom highlight was rendered and the default should be skipped
     */
    def renderHighlight(player: EntityPlayer, hit: RayTraceResult, mcrFactory: CommonMicroFactory, size: Int, material: Int): Boolean
}

object MicroMaterialRegistry {
    private val typeMap = MHashMap[String, IMicroMaterial]()
    private case class MaterialMap(entries: Array[(String, IMicroMaterial)], names: Map[String, Int], writer: IDWriter, missingId: Int)
    @volatile private var materialMap: MaterialMap = null

    private def buildMap(entries: Array[(String, IMicroMaterial)]): MaterialMap = {
        val names = entries.indices.map(i => entries(i)._1 -> i).toMap
        val writer = new IDWriter
        writer.setMax(entries.length)
        val missingId = names.getOrElse(MissingMicroMaterial.getMaterialID,
            throw new IllegalStateException("Missing microblock material placeholder is not registered"))
        MaterialMap(entries, names, writer, missingId)
    }

    private val highlightRenderers = ListBuffer[IMicroHighlightRenderer]()
    private var maxCuttingStrength: Int = scala.compiletime.uninitialized

    private val remap = MHashMap[String, String]()

    @volatile private var iconsLoaded = false

    /**
     * Register a micro material with unique identifier name
     */
    def registerMaterial(material: IMicroMaterial, name: String): Unit = {
        if (MultiPartRegistry.loaded) {
            throw new IllegalStateException("You must register your materials in the init methods.")
        }

        if (typeMap.contains(name)) {
            logger.error("Material with id " + name + " is already registered.")
            return
        }

        logger.debug("Registered micro material: " + name)

        typeMap.put(name, material)
    }

    /**
     * Replace a micro material with unique identifier name
     */
    def replaceMaterial(material: IMicroMaterial, name: String): Unit = {
        if (MultiPartRegistry.loaded) {
            throw new IllegalStateException("You must register your materials in the init methods.")
        }

        if (typeMap.remove(name).isEmpty) {
            logger.error("Material with id " + name + " was not registered.")
        }

        logger.debug("Replaced micro material: " + name)

        typeMap.put(name, material)
    }

    /**
     * Registers a highlight renderer
     */
    def registerHighlightRenderer(handler: IMicroHighlightRenderer): Unit = {
        highlightRenderers += handler
    }

    def remapName(oldName: String, newName: String): Unit = remap.put(oldName, newName)

    private[microblock] def setupIDMap(): Unit = {
        val materials = typeMap.toList.filterNot(_._1 == MissingMicroMaterial.getMaterialID).sortBy(_._2)
        materialMap = buildMap((materials :+ (MissingMicroMaterial.getMaterialID -> MissingMicroMaterial)).toArray)
    }

    private[microblock] def calcMaxCuttingStrength(): Unit = {
        val it = Item.REGISTRY.iterator
        maxCuttingStrength = it.asScala.flatMap {
            case saw: Saw => Some(saw.getMaxCuttingStrength)
            case _ => None
        }.max
    }

    def markIconReload(): Unit = synchronized {
        iconsLoaded = false
    }

    private[microblock] def loadIcons(): Unit = {
        if (!iconsLoaded) synchronized {
            if (!iconsLoaded) {
                val current = materialMap
                if (current != null) {
                    current.entries.foreach(e => e._2.loadIcons())
                    iconsLoaded = true
                }
            }
        }
    }

    def getMaxCuttingStrength = maxCuttingStrength

    def writeIDMap(packet: PacketCustom): Unit = {
        val current = materialMap
        packet.writeInt(current.entries.length)
        current.entries.foreach(e => packet.writeString(e._1))
    }

    def readIDMap(packet: PacketCustom): Seq[String] = {
        val k = packet.readInt()
        val entries = new Array[(String, IMicroMaterial)](k)
        val missing = ListBuffer[String]()
        for (i <- 0 until k) {
            val s = packet.readString()
            val v = typeMap.get(s)
            if (v.isEmpty) {
                missing += s
            } else {
                entries(i) = (s, v.get)
            }
        }

        // Publish entries, lookup and wire encoding together, after full validation.
        if (missing.isEmpty && !entries.exists(_._1 == MissingMicroMaterial.getMaterialID))
            missing += MissingMicroMaterial.getMaterialID
        if (missing.isEmpty) materialMap = buildMap(entries)
        missing.toSeq
    }

    def writeMaterialID(data: MCDataOutput, id: Int): Unit = {
        materialMap.writer.write(data, id)
    }

    def readMaterialID(data: MCDataInput) = materialMap.writer.read(data)

    def materialName(id: Int) = materialMap.entries(id)._1

    def materialID(name: String): Int = {
        val current = materialMap
        current.names.get(remap.getOrElse(name, name)) match {
            case Some(v) => v
            case None =>
                logger.error("Missing mapping for part with ID: " + name)
                current.missingId
        }
    }

    def getMaterial(name: String) = typeMap.getOrElse(remap.getOrElse(name, name), null)

    def getMaterial(id: Int): IMicroMaterial = {
        val current = materialMap
        if (current != null && current.entries.isDefinedAt(id)) current.entries(id)._2 else null
    }

    def getIdMap = if (materialMap == null) null else materialMap.entries

    def renderHighlight(player: EntityPlayer, hit: RayTraceResult, mcrClass: CommonMicroFactory, size: Int, material: Int): Boolean = {
        val overridden = highlightRenderers.find(_.renderHighlight(player, hit, mcrClass, size, material))
        if (overridden.isDefined) {
            return true
        }

        MicroblockRender.renderHighlight(player, hit, mcrClass, size, material)
        true
    }
}
