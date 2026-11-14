package codechicken.microblock

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.raytracer.CuboidRayTraceResult
import codechicken.lib.render.CCRenderState
import codechicken.lib.vec.Cuboid6
import codechicken.lib.vec.Vector3
import codechicken.microblock.MicroMaterialRegistry._
import codechicken.multipart._
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.BlockRenderLayer
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import scala.util.boundary

abstract class Microblock(var material: Int = 0) extends TMultiPart with TCuboidPart {
    var shape: Byte = 0
    private var missingMaterialName: Option[String] = None

    private def savedMaterialName: String =
        if (getIMaterial == MissingMicroMaterial) missingMaterialName.getOrElse(materialName(material))
        else materialName(material)

    def microFactory: MicroblockFactory

    def getType: ResourceLocation = microFactory.getName

    override def getStrength(player: EntityPlayer, hit: CuboidRayTraceResult): Float = getIMaterial match {
        case null => super.getStrength(player, hit)
        case mat => mat.getStrength(player)
    }

    def getSize: Int = shape >> 4

    def getShapeSlot: Int = shape & 0xF

    /**
     * General purpose microblock description value. These values are only used by
     * subclass overrides in this class, so they can be whatever you would like.
     *
     * @param size A 28 bit value representing the current size
     * @param slot A 4 bit value representing the current slot
     */
    def setShape(size: Int, slot: Int): Unit = {
        shape = (size << 4 | slot).toByte
    }

    def getMaterial: Int = material

    def getIMaterial: IMicroMaterial = MicroMaterialRegistry.getMaterial(material)

    /**
     * The factory ID that will be put into the ItemStack damage value
     */
    def itemFactoryID: Int

    override def getDrops: java.util.List[ItemStack] = {
        var size = getSize
        val items = ListBuffer[ItemStack]()
        for (s <- Seq(4, 2, 1)) {
            val m = size / s
            size -= m * s
            if (m > 0) {
                items += ItemMicroPart.createStack(m, ItemMicroPart.damage(itemFactoryID, s), savedMaterialName)
            }
        }
        items.asJava
    }

    override def pickItem(hit: CuboidRayTraceResult): ItemStack = {
        boundary:
            val size = getSize
            for (s <- Seq(4, 2, 1))
                if (size % s == 0 && size / s >= 1) {
                    boundary.break(ItemMicroPart.create(itemFactoryID, size, savedMaterialName))
                }

            null //unreachable
    }

    override def writeDesc(packet: MCDataOutput): Unit = {
        writeMaterialID(packet, material) // Read by IDynamicPartFactory.
        writeShape(packet)
    }

    override def readDesc(packet: MCDataInput): Unit = {
        // The factory has already read the material ID.
        shape = packet.readByte
        missingMaterialName = if (getIMaterial == MissingMicroMaterial) Some(packet.readString()) else None
    }

    private def writeShape(packet: MCDataOutput): Unit = {
        packet.writeByte(shape)
        if (getIMaterial == MissingMicroMaterial) packet.writeString(savedMaterialName)
    }

    def sendShapeUpdate(): Unit = writeShape(getWriteStream)

    // Existing parts already know their material ID; only the factory reads it.
    override def sendDescUpdate(): Unit = if (tile != null) sendShapeUpdate()

    override def read(packet: MCDataInput): Unit = {
        super.read(packet)
        tile.notifyPartChange(this)
    }

    override def save(tag: NBTTagCompound): Unit = {
        tag.setByte("shape", shape)
        tag.setString("material", savedMaterialName)
    }

    override def load(tag: NBTTagCompound): Unit = {
        shape = tag.getByte("shape")
        val originalName = tag.getString("material")
        material = materialID(originalName)
        missingMaterialName = if (getIMaterial == MissingMicroMaterial) Some(originalName) else None
    }

    def isTransparent: Boolean = getIMaterial.isTransparent

    override def getLightValue: Int = getIMaterial.getLightValue

    override def getExplosionResistance(entity: Entity): Float = getIMaterial.explosionResistance(entity) * microFactory.getResistanceFactor
}

trait MicroblockClient extends Microblock with TIconHitEffectsPart with IMicroMaterialRender {
    @SideOnly(Side.CLIENT)
    override def getBreakingIcon(hit: CuboidRayTraceResult): TextureAtlasSprite = getBrokenIcon(hit.sideHit.ordinal)

    @SideOnly(Side.CLIENT)
    override def getBrokenIcon(side: Int): TextureAtlasSprite = getIMaterial match {
        case null => ModelLoader.White.INSTANCE
        case mat => mat.getBreakingIcon(side)
    }

    override def renderStatic(pos: Vector3, layer: BlockRenderLayer, ccrs: CCRenderState): Boolean = {
        if (layer != null && getIMaterial.canRenderInLayer(layer)) {
            render(pos, layer, ccrs)
            true
        } else {
            false
        }
    }

    /**
     * Called to add the vertecies of this part to the CCRenderState
     *
     * @param pos   The position of this part
     * @param layer The block layer, null for inventory rendering
     * @param ccrs  The CCRenderState to add the verts to
     */
    def render(pos: Vector3, layer: BlockRenderLayer, ccrs: CCRenderState): Unit

    override def getRenderBounds: Cuboid6 = getBounds
}

trait CommonMicroblockClient extends CommonMicroblock with MicroblockClient with TMicroOcclusionClient {
    override def render(pos: Vector3, layer: BlockRenderLayer, ccrs: CCRenderState): Unit = {
        if (layer == null) {
            MicroblockRender.renderCuboid(pos, ccrs, getIMaterial, layer, getBounds, 0)
        } else {
            MicroblockRender.renderCuboid(pos, ccrs, getIMaterial, layer, renderBounds, renderMask)
        }
    }
}

trait CommonMicroblock extends Microblock with TPartialOcclusionPart with TMicroOcclusion with TSlottedPart {
    def microFactory: CommonMicroFactory

    def getSlot: Int = getShapeSlot

    def getSlotMask: Int = 1 << getSlot

    def getPartialOcclusionBoxes: java.util.List[Cuboid6] = Seq(getBounds).asJava

    override def itemFactoryID: Int = microFactory.getFactoryID
}
