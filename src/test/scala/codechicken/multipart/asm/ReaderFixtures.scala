package codechicken.multipart.asm

class ReaderBase
trait ReaderParent extends ReaderBase
trait ReaderChild extends ReaderParent
trait ReaderGeneric[A] extends ReaderBase
class ReaderApplied extends ReaderGeneric[String] {
    // The header reader must not need to resolve any names in this body.
    def onlyInTheBody = classOf[net.minecraft.client.Minecraft]
}
