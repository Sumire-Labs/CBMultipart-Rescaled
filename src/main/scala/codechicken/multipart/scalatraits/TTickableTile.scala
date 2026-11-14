package codechicken.multipart.scalatraits

import java.util.{LinkedList => JLinkedList}

import codechicken.multipart.{TMultiPart, TileMultipart}
import net.minecraft.util.ITickable

import scala.jdk.CollectionConverters._

/**
 * Mixin Trait for parts implementing ITickable. Allows parts
 * to receive update calls every tick.
 */
trait TTickableTile extends TileMultipart with ITickable {

    private var doesTick = false
    var tickingParts = new JLinkedList[TMultiPart & ITickable]() //cache to reduce iteration of all parts

    override def copyFrom(that: TileMultipart): Unit = {
        super.copyFrom(that)
        that match {
            case tile: TTickableTile => {
                tickingParts = tile.tickingParts
                doesTick = tile.doesTick
            }
            case _ =>
        }
    }

    override def bindPart(part: TMultiPart): Unit = {
        super.bindPart(part)
        part match {
            case tickingPart: ITickable =>
                tickingParts.asScala += tickingPart
                setTicking(true)
            case _ =>
        }
    }

    override def partRemoved(part: TMultiPart, p: Int): Unit = {
        super.partRemoved(part, p)
        part match {
            case tickingPart: ITickable => tickingParts.asScala -= tickingPart
            //No need to disable ticking if empty, this tile will be replaced w/ nonticking one anyway
            case _ =>
        }
    }

    override def clearParts(): Unit = {
        super.clearParts()
        tickingParts.clear()
    }

    override def update(): Unit = {
        // Callbacks can add or remove parts; new parts start on the next tick.
        val it = tickingParts.asScala.toArray.iterator
        while (it.hasNext) {
            val p = it.next()
            if (p.tile eq this) p.update()
        }
    }

    override def loadFrom(that: TileMultipart): Unit = {
        super.loadFrom(that)
        // Let World defer registration if it is currently ticking tile entities.
        if (doesTick) {
            getWorld.addTileEntity(this)
        }
    }

    override def loadTo(that: TileMultipart): Unit = {
        super.loadTo(that)
        // The old tile is invalid; queue removal without changing World's iterator.
        if (doesTick) {
            getWorld.markTileEntityForRemoval(this)
        }
    }

    private def setTicking(tick: Boolean): Unit = {
        if (doesTick == tick) return
        doesTick = tick

        if (getWorld != null && getWorld.getTileEntity(getPos) == this) {
            if (tick) {
                getWorld.addTileEntity(this)
            } else {
                getWorld.tickableTileEntities.remove(this)
            }
        }
    }
}

/**
 * To handle obfuscation issues, this is registered as a java trait.
 */
class JTickableTile extends TileMultipart with TTickableTile {
}
