package codechicken.multipart

import codechicken.lib.render.CCRenderState
import codechicken.lib.vec.{Cuboid6, Vector3}
import codechicken.microblock.{BlockMicroMaterial, DemonicaCompat, MicroblockRender, MissingMicroMaterial}
import codechicken.microblock.MicroMaterialRegistry
import codechicken.microblock.handler.MicroblockCPH
import codechicken.lib.packet.PacketCustom
import codechicken.microblock.FaceMicroFactory
import codechicken.multipart.handler.MultipartProxy
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.init.Blocks
import net.minecraft.util.{BlockRenderLayer, ResourceLocation}
import net.minecraft.util.ScreenShotHelper
import net.minecraft.world.{GameType, WorldSettings, WorldType}
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

object ClientSmokeChecks {
    private var launched = false
    private var tested = false
    private val sceneBlocks = Seq(() => Blocks.STONE, () => Blocks.GLOWSTONE, () => Blocks.GLASS)
    private val sceneKeys = Seq("cbmultipart_smoke:stone", "cbmultipart_smoke:glowstone", "cbmultipart_smoke:glass")
    private val rendered = sceneKeys.map(_ => new AtomicInteger())
    private var sceneOrigin: net.minecraft.util.math.BlockPos = null
    private var sceneStarted = 0
    private var sceneTask: java.util.concurrent.Future[?] = null

    def registerMaterials(): Unit = {
        sceneKeys.indices.foreach { index =>
            MicroMaterialRegistry.registerMaterial(new BlockMicroMaterial(sceneBlocks(index)().getDefaultState, sceneKeys(index)) {
                override def getMicroRenderOps(pos: Vector3, side: Int, layer: BlockRenderLayer, bounds: Cuboid6) = {
                    rendered(index).incrementAndGet()
                    super.getMicroRenderOps(pos, side, layer, bounds)
                }
            }, sceneKeys(index))
        }
    }
    def start(): Unit = {
        Minecraft.getMinecraft.gameSettings.pauseOnLostFocus = false
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    def tick(event: TickEvent.ClientTickEvent): Unit = {
        if (event.phase != TickEvent.Phase.END || tested) return
        val mc = Minecraft.getMinecraft
        if (!launched && mc.currentScreen.isInstanceOf[GuiMainMenu]) {
            launched = true
            val settings = new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT).enableCommands()
            // Each run starts from untouched blocks, independent of previous fixture saves.
            mc.launchIntegratedServer("multipart-smoke-" + java.lang.Long.toUnsignedString(System.nanoTime()), "Multipart Smoke", settings)
        } else if (launched && mc.world != null && mc.player != null && mc.player.ticksExisted > 40 &&
            mc.world.isBlockLoaded(mc.player.getPosition)) {
            if (sceneTask == null) {
                mc.player.rotationYaw = 0
                mc.player.rotationPitch = 20
                sceneOrigin = mc.player.getPosition.add(-1, 0, 3)
                sceneStarted = mc.player.ticksExisted
                sceneTask = mc.getIntegratedServer.addScheduledTask(new Runnable {
                    override def run(): Unit = {
                        val world = mc.getIntegratedServer.getWorld(0)
                        sceneKeys.indices.foreach { index =>
                            val pos = sceneOrigin.add(index, 0, 0)
                            world.setBlockToAir(pos)
                            world.setBlockState(pos.down(), Blocks.STONE.getDefaultState)
                            val part = FaceMicroFactory.create(false, MicroMaterialRegistry.materialID(sceneKeys(index)))
                            part.setShape(4, 0)
                            TileMultipart.addPart(world, pos, part)
                        }
                    }
                })
                return
            }
            if ((mc.player.ticksExisted - sceneStarted < 60 || rendered.exists(_.get() == 0)) &&
                mc.player.ticksExisted - sceneStarted < 240) return
            tested = true
            try {
                sceneTask.get()
                assert(rendered.forall(_.get() > 0), "World renderer skipped microblock materials: " + rendered.map(_.get()))
                sceneKeys.indices.foreach { index =>
                    val tile = BlockMultipart.getTile(mc.world, sceneOrigin.add(index, 0, 0))
                    assert(tile != null && tile.partList.size == 1,
                        "Server microblocks did not synchronize at " + sceneOrigin.add(index, 0, 0) +
                            ": " + (if (tile == null) "no tile" else tile.partList.size + " parts"))
                }
                println("SCENE_RENDER_PASS: stone, glowstone and glass through the actual world chunk renderer")
                val world = mc.world
                val pos = mc.player.getPosition.add(2, 2, 2)
                world.setBlockState(pos, Blocks.TORCH.getDefaultState, 0)
                val added = new TMultiPart {
                    override def getType = new ResourceLocation("cbmultipart_smoke:client_part")
                }
                val tile = MultipartGenerator.addPart(world, pos, added)
                assert(tile.partList == Seq(added), "Stale client block must not be converted into an extra part")
                val originalMap = MicroMaterialRegistry.getIdMap
                val redundant = new PacketCustom("ForgeMicroblock", 1)
                redundant.writeInt(originalMap.length)
                originalMap.reverse.foreach(e => redundant.writeString(e._1))
                MicroblockCPH.handlePacket(new PacketCustom(redundant.toPacketBuffer), mc, null)
                assert(MicroMaterialRegistry.getIdMap eq originalMap, "Integrated client rewrote the server's material map")
                val buffer = new BufferBuilder(32768)
                buffer.begin(7, DefaultVertexFormats.BLOCK)
                MultipartRenderer.renderBlock(world, pos, MultipartProxy.block.getDefaultState, buffer)
                assert(CCRenderState.instance().lightMatrix.access == null, "Renderer retained its world")
                buffer.finishDrawing()
                if (Loader.isModLoaded("demonica")) shaderMaterials(buffer, pos)
                ScreenShotHelper.saveScreenshot(mc.gameDir, "client-scene.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer)
                Files.writeString(Path.of(System.getProperty("cbmultipart.client.result")), "PASS\n")
                println("CLIENT_SMOKE_PASS: stale conversion, renderer cleanup, optional shader quad materials")
            } catch {
                case error: Throwable =>
                    error.printStackTrace()
            } finally {
                mc.shutdown()
            }
        }
    }

    private def shaderMaterials(buffer: BufferBuilder, pos: net.minecraft.util.math.BlockPos): Unit = {
        val loader = getClass.getClassLoader
        val overrides = Class.forName("net.coderbot.iris.celeritas.buffer.ShaderMaterialOverrideState", true, loader)
        val getId = overrides.getMethod("getBlockId")
        val setId = overrides.getMethod("setBlockId", Integer.TYPE)
        val contextClass = Class.forName("com.demonica.celeritas.api.shader.vertex.VanillaQuadContext", true, loader)
        val extension = Class.forName("com.demonica.celeritas.api.shader.vertex.BufferBuilderExtension", true, loader)
        val context = contextClass.getConstructors.head.newInstance(Int.box(0), Int.box(0), Int.box(0), Int.box(-1), Short.box(0), Byte.box(0))
        val stone = new BlockMicroMaterial(Blocks.STONE.getDefaultState, "smoke:stone")
        val glowstone = new BlockMicroMaterial(Blocks.GLOWSTONE.getDefaultState, "smoke:glowstone")
        stone.loadIcons()
        glowstone.loadIcons()
        setId.invoke(null, Int.box(-1))
        DemonicaCompat.withMaterial(stone, true) {
            assert(getId.invoke(null) == Int.box(6001), "Test shader pack did not map stone")
            DemonicaCompat.withMaterial(glowstone, false) {
                assert(getId.invoke(null) == Int.box(6001), "Inventory rendering changed the terrain material")
            }
            try {
                DemonicaCompat.withMaterial(glowstone, true) {
                    assert(getId.invoke(null) == Int.box(6002))
                    throw new IllegalStateException("expected draw failure")
                }
            } catch { case e: IllegalStateException if e.getMessage == "expected draw failure" => }
            assert(getId.invoke(null) == Int.box(6001), "Nested/failed render lost its enclosing material")
        }
        assert(getId.invoke(null) == Int.box(-1))
        buffer.begin(7, DefaultVertexFormats.BLOCK)
        extension.getMethod("demonica$setActiveQuadContext", contextClass).invoke(buffer, context)
        val ccrs = CCRenderState.instance()
        ccrs.reset()
        ccrs.bind(buffer)
        ccrs.lightMatrix.locate(Minecraft.getMinecraft.world, pos)
        try {
            MicroblockRender.renderCuboid(Vector3.zero, ccrs, stone, BlockRenderLayer.SOLID, Cuboid6.full, 0)
            MicroblockRender.renderCuboid(Vector3.zero, ccrs, glowstone, BlockRenderLayer.SOLID, Cuboid6.full, 0)
            MicroblockRender.renderCuboid(Vector3.zero, ccrs, MissingMicroMaterial, BlockRenderLayer.SOLID, Cuboid6.full, 0)
        } finally { ccrs.lightMatrix.access = null }
        val contexts = extension.getMethod("demonica$consumeQuadContexts").invoke(buffer).asInstanceOf[java.util.List[?]].asScala
        val ids = contexts.map(c => contextClass.getMethod("blockStateId").invoke(c).asInstanceOf[Integer].intValue())
        assert(ids.toSeq == Seq.fill(6)(6001) ++ Seq.fill(6)(6002) ++ Seq.fill(6)(-1),
            "Microblock quads inherited another material: " + ids)
        assert(getId.invoke(null) == Int.box(-1), "Material override leaked into the next block")
        buffer.finishDrawing()
        println("SHADER_MATERIAL_PASS: stone/glowstone/missing quads, nested and exceptional scope restoration")
    }
}
