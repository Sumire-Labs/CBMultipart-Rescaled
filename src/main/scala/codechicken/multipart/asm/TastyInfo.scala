package codechicken.multipart.asm

import net.minecraft.launchwrapper.Launch

import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** The small part of Scala 3 TASTy metadata required by the mixin compiler. */
final case class TastyInfo(name: String, isTrait: Boolean, parents: Seq[String]) {
    def traitParents: Seq[String] = parents.filterNot(TastyInfo.isConcreteClass)

    def classParent: Option[String] = parents.find(TastyInfo.isConcreteClass)
}

object TastyInfo {
    private val cache = new ConcurrentHashMap[String, TastyInfo]()

    private def loaders: Seq[ClassLoader] = Seq(
        Launch.classLoader,
        Thread.currentThread().getContextClassLoader,
        getClass.getClassLoader,
        ASMMixinCompiler.cl
    ).filter(_ != null).distinct

    private def resourceNames(name: String): Iterator[String] =
        Iterator.iterate(name.replace('.', '/')) { current =>
            val separator = current.lastIndexOf('$')
            if (separator > current.lastIndexOf('/')) current.substring(0, separator) else ""
        }.takeWhile(_.nonEmpty).map(_ + ".tasty")

    def readIfPresent(className: String): Option[TastyInfo] = {
        val name = className.replace('/', '.')
        val available = loaders
        if (resourceNames(name).exists(resource => available.exists(_.getResource(resource) != null))) Some(read(name)) else None
    }

    def read(className: String): TastyInfo = {
        val name = className.replace('/', '.')
        val cached = cache.get(name)
        if (cached != null) return cached

        val info = readUncached(name)
        val previous = cache.putIfAbsent(name, info)
        if (previous != null) previous else info
    }

    private def isConcreteClass(name: String): Boolean = {
        val internalName = name.replace('.', '/')
        val bytes = ASMMixinCompiler.getBytes(internalName)
        if (bytes != null) {
            val node = new org.objectweb.asm.tree.ClassNode()
            new org.objectweb.asm.ClassReader(bytes).accept(node, org.objectweb.asm.ClassReader.EXPAND_FRAMES)
            (node.access & org.objectweb.asm.Opcodes.ACC_INTERFACE) == 0
        } else {
            try {
                val clazz = ASMMixinCompiler.cl.loadClass(name)
                !clazz.isInterface
            } catch {
                case _: Throwable => false
            }
        }
    }

    private def readUncached(name: String): TastyInfo = {
        // Nested classes share the top-level owner's TASTy file.
        val stream = resourceNames(name).flatMap(resourceStream).nextOption().getOrElse {
            throw new IllegalArgumentException("Scala 3 TASTy resource not found for " + name)
        }
        try {
            val data = TastyReader.read(stream.readAllBytes(), name)
            TastyInfo(name, data.head.toBoolean, data.tail.toSeq)
        } finally {
            stream.close()
        }
    }

    private def resourceStream(resourceName: String): Option[InputStream] = {
        loaders.iterator.map(_.getResourceAsStream(resourceName)).find(_ != null)
    }

}
