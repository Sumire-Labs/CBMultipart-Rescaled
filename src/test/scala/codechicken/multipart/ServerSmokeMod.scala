package codechicken.multipart

import codechicken.multipart.asm.MultipartMixinFactory
import codechicken.multipart.minecraft.{ButtonPart, LeverPart}
import net.minecraft.block.BlockButton
import net.minecraft.init.Blocks
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ITickable
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.{FMLCommonHandler, Mod}
import net.minecraftforge.fml.common.event.FMLServerStartedEvent
import java.nio.file.{Files, Path}
import java.util.BitSet

/** Loaded only when test output is explicitly added by -Psmoke_test=true. */
@Mod(modid = "cbmultipart_smoke", name = "CBMultipart integration tests", version = "1",
    dependencies = "required-after:microblockcbe")
class ServerSmokeMod {
    @Mod.EventHandler
    def started(event: FMLServerStartedEvent): Unit = {
        val result = System.getProperty("cbmultipart.smoke.result")
        if (result == null) return
        val server = FMLCommonHandler.instance().getMinecraftServerInstance
        try {
            val names = Seq("JTickableTile", "TSlottedTile", "JInventoryTile", "TPartialOcclusionTile",
                "TRedstoneTile", "TTileChangeTile").map("codechicken.multipart.scalatraits." + _)
            val ids = names.map(name => MultipartMixinFactory.getId(name.replace('.', '/')))
            for (mask <- 0 until (1 << ids.size)) {
                val bits = new BitSet()
                ids.indices.filter(i => (mask & (1 << i)) != 0).foreach(i => bits.set(ids(i)))
                val tile = MultipartMixinFactory.construct(bits)
                assert(tile != null)
                tile.copyFrom(MultipartMixinFactory.construct(bits))
                if ((mask & 1) != 0) {
                    assert(tile.isInstanceOf[ITickable])
                    tile.asInstanceOf[ITickable].update()
                }
            }
            val passThroughName = MultipartMixinFactory.generatePassThroughTrait(classOf[SmokePassThrough].getName)
            val passThroughBits = new BitSet()
            passThroughBits.set(MultipartMixinFactory.getId(passThroughName))
            val passThroughTile = MultipartMixinFactory.construct(passThroughBits)
            val part = new TMultiPart with SmokePassThrough {
                override def getType = new ResourceLocation("cbmultipart_smoke:passthrough")
                override def signal() = 42
            }
            passThroughTile.addPart_do(part)
            assert(passThroughTile.asInstanceOf[SmokePassThrough].signal() == 42)
            val copied = MultipartMixinFactory.construct(passThroughBits)
            copied.copyFrom(passThroughTile)
            assert(copied.asInstanceOf[SmokePassThrough].signal() == 42)

            val pressed = Blocks.STONE_BUTTON.getDefaultState.withProperty(BlockButton.POWERED, java.lang.Boolean.TRUE)
            assert(!new ButtonPart(pressed).pressed(), "Converting a pressed button must reset it")

            val tile = MultipartGenerator.generateCompositeTile(null, List(new LeverPart()), false)
            tile.addPart_do(new LeverPart())
            val tag = tile.writeToNBT(new NBTTagCompound())
            val restored = TileMultipart.createFromNBT(tag)
            assert(restored != null && restored.partList.size == 1)
            assert(restored.partList.head.getType == tile.partList.head.getType)
            assert(restored.partList.head.tile eq restored)
            assert(TileEntity.REGISTRY.getNameForObject(restored.getClass) ==
                codechicken.multipart.handler.MultipartSaveLoad.TILE_ID)

            Files.writeString(Path.of(result), "PASS\n")
            System.out.println("CBMULTIPART_SMOKE_PASS: 64 trait combinations, Java passthrough, button conversion and NBT round trip")
        } finally {
            server.initiateShutdown()
        }
    }
}
