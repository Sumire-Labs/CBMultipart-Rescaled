package codechicken.multipart

import codechicken.lib.packet.PacketCustom
import codechicken.microblock.MicroMaterialRegistry
import codechicken.microblock.{FaceMicroFactory, MicroRecipe}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import codechicken.multipart.asm.MultipartMixinFactory
import net.minecraft.util.{ITickable, ResourceLocation}
import java.util.BitSet
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

object GtnhRegressionChecks {
    private def part() = new TMultiPart {
        override def getType = new ResourceLocation("cbmultipart_smoke:regression")
    }

    def run(): Unit = {
        val failures = ListBuffer[String]()
        def check(name: String)(test: => Unit): Unit = {
            try { test; println("REGRESSION_PASS: " + name) }
            catch {
                case NonFatal(error) =>
                    failures += name
                    println("REGRESSION_FAIL: " + name + ": " + error)
            }
        }
        check("detached part description update") {
            part().sendDescUpdate()
        }
        check("empty multipart light value") {
            assert(new TileMultipart().getLightValue == 0)
        }
        check("callbacks after part ownership transfer") {
            val oldTile = new TileMultipart()
            val moved = part()
            oldTile.addPart_do(moved)
            moved.bind(new TileMultipart())
            var callbacks = 0
            oldTile.operate(_ => callbacks += 1)
            assert(callbacks == 0)
        }
        check("ticks after part ownership transfer") {
            val bits = new BitSet()
            bits.set(MultipartMixinFactory.getId("codechicken/multipart/scalatraits/JTickableTile"))
            val oldTile = MultipartMixinFactory.construct(bits)
            var ticks = 0
            val moved = new TMultiPart with ITickable {
                override def getType = new ResourceLocation("cbmultipart_smoke:tick")
                override def update(): Unit = ticks += 1
            }
            oldTile.addPart_do(moved)
            moved.bind(new TileMultipart())
            oldTile.asInstanceOf[ITickable].update()
            assert(ticks == 0)
        }
        check("missing material is not an unrelated real block") {
            val id = MicroMaterialRegistry.materialID("cbmultipart_smoke:removed_material")
            val material = MicroMaterialRegistry.getMaterial(id)
            assert(material != null && material.getMaterialID == "microblockcbe:missing")
        }
        check("missing material survives save and drops without entering recipes") {
            val original = "cbmultipart_smoke:removed_material"
            val missing = MicroMaterialRegistry.materialID(original)
            val micro = FaceMicroFactory.create(false, missing)
            val saved = new NBTTagCompound()
            saved.setString("material", original)
            saved.setByte("shape", 0x11.toByte)
            micro.load(saved)
            val roundTrip = new NBTTagCompound()
            micro.save(roundTrip)
            assert(roundTrip.getString("material") == original)
            assert(micro.getDrops.get(0).getTagCompound.getString("mat") == original)
            assert(MicroRecipe.create(1, 0, 8, missing).isEmpty)
            val stone = MicroRecipe.findMaterial(new ItemStack(Blocks.STONE))
            assert(stone >= 0 && stone != missing)
        }
        check("rejected material handshake preserves the active map") {
            val original = MicroMaterialRegistry.getIdMap
            def packet() = {
                val out = new PacketCustom("ForgeMicroblock", 1)
                out.writeInt(original.length)
                original.foreach(entry => out.writeString(entry._1))
                out.toPacketBuffer
            }
            val rejected = new PacketCustom("ForgeMicroblock", 1)
            rejected.writeInt(2)
            rejected.writeString(original.head._1)
            rejected.writeString("cbmultipart_smoke:server_only_material")
            val input = new PacketCustom(rejected.toPacketBuffer)
            try {
                assert(MicroMaterialRegistry.readIDMap(input).nonEmpty)
                assert(MicroMaterialRegistry.getIdMap eq original)
            } finally {
                MicroMaterialRegistry.readIDMap(new PacketCustom(packet()))
            }
        }
        assert(failures.isEmpty, "GTNH regression failures: " + failures.mkString(", "))
    }
}
