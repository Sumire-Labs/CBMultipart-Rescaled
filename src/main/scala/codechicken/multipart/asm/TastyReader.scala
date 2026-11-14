package codechicken.multipart.asm

import dotty.tools.dotc.core.tasty.TastyUnpickler
import dotty.tools.dotc.core.tasty.TastyUnpickler.{NameTable, SectionUnpickler}
import dotty.tools.tasty.{TastyReader as ByteReader}
import dotty.tools.tasty.TastyBuffer.Addr
import dotty.tools.tasty.TastyFormat.*
import scala.collection.mutable.ListBuffer

/** Reads class headers without resolving method bodies against Minecraft's namespace. */
object TastyReader {
    private[asm] final class MissingClassHeader(target: String)
        extends IllegalArgumentException("No Scala 3 class header for " + target)

    def read(bytes: Array[Byte], target: String): Array[String] = metadata(bytes, target)._1

    private[asm] def readLazyGetters(bytes: Array[Byte], target: String): Set[String] = metadata(bytes, target)._2

    private def metadata(bytes: Array[Byte], target: String): (Array[String], Set[String]) = {
        new TastyUnpickler(bytes).unpickle(new SectionUnpickler[(Array[String], Set[String])]("ASTs") {
            override def unpickle(reader: ByteReader, names: NameTable): (Array[String], Set[String]) = {
                new Headers(reader, names).scan(reader.endAddr, "", false, target).getOrElse {
                    throw new MissingClassHeader(target)
                }
            }
        }).getOrElse {
            throw new IllegalArgumentException("Missing TASTy AST section for " + target)
        }
    }

    private final class Headers(val reader: ByteReader, names: NameTable) {
        import reader.*
        private def name(): String = names(readNameRef()).toString
        private def fork(addr: Addr): Headers = new Headers(subReader(addr, endAddr), names)

        private def skip(): Unit = {
            val tag = readByte()
            if (tag >= firstLengthTreeTag) goto(readEnd())
            else if (tag >= firstNatASTTreeTag) { readNat(); skip() }
            else if (tag >= firstASTTreeTag) skip()
            else if (tag >= firstNatTreeTag) readLongInt()
        }

        private def join(prefix: String, leaf: String): String =
            if (prefix.isEmpty || prefix == "<root>" || prefix == "<empty>") leaf
            else if (prefix.endsWith("$")) prefix + leaf
            else prefix + "." + leaf

        private def symbolName(addr: Addr): String = {
            val r = subReader(addr, endAddr)
            r.readByte(); r.readEnd()
            names(r.readNameRef()).toString
        }

        private def path(): String = {
            val tag = readByte()
            tag match {
                case SHAREDterm | SHAREDtype => fork(readAddr()).path()
                case TERMREFpkg | TYPEREFpkg => name()
                case TYPEREF | TERMREF | SELECTtpt | SELECT =>
                    val leaf = name()
                    join(path(), leaf)
                case IDENT | IDENTtpt =>
                    val fallback = name()
                    val underlying = path()
                    if (underlying.nonEmpty) underlying else fallback
                case TYPEREFdirect | TERMREFdirect => symbolName(readAddr())
                case TYPEREFsymbol | TERMREFsymbol =>
                    val leaf = symbolName(readAddr())
                    join(path(), leaf)
                case NEW | THIS | QUALTHIS | SINGLETONtpt | EXPLICITtpt => path()
                case APPLY | TYPEAPPLY | BLOCK | TYPED | APPLIEDtype | APPLIEDtpt |
                     ANNOTATEDtype | ANNOTATEDtpt =>
                    val end = readEnd()
                    val result = path()
                    goto(end)
                    result
                case SELECTin =>
                    val end = readEnd()
                    name() // Constructor signature; the NEW qualifier supplies the class.
                    val result = path()
                    goto(end)
                    result
                case TYPEREFin | TERMREFin =>
                    val end = readEnd()
                    val leaf = name()
                    val result = join(path(), leaf)
                    goto(end)
                    result
                case _ => throw new IllegalArgumentException("Unsupported TASTy parent tag " + astTagToString(tag))
            }
        }

        private def lazyFields(start: Addr, end: Addr): Set[String] = {
            val body = fork(start)
            val cursor = body.reader
            val result = Set.newBuilder[String]
            while (cursor.currentAddr.index < end.index) {
                if (cursor.nextByte == VALDEF) {
                    cursor.readByte()
                    val fieldEnd = cursor.readEnd()
                    val fieldName = body.name()
                    while (cursor.currentAddr.index < fieldEnd.index) {
                        if (cursor.nextByte == LAZY) result += scala.reflect.NameTransformer.encode(fieldName)
                        body.skip()
                    }
                } else body.skip()
            }
            result.result()
        }

        def scan(end: Addr, owner: String, nested: Boolean, target: String): Option[(Array[String], Set[String])] = {
            while (currentAddr.index < end.index) {
                nextByte match {
                    case PACKAGE =>
                        readByte()
                        val packageEnd = readEnd()
                        val packageName = path()
                        val found = scan(packageEnd, packageName, false, target)
                        if (found.nonEmpty) return found
                        goto(packageEnd)
                    case TYPEDEF =>
                        readByte()
                        val definitionEnd = readEnd()
                        val simple = name()
                        val fullName = if (nested) owner.stripSuffix("$") + "$" + simple else join(owner, simple)
                        if (nextByte == TEMPLATE) {
                            readByte()
                            val templateEnd = readEnd()
                            while (isParamTag(nextByte) || nextByte == EMPTYCLAUSE || nextByte == SPLITCLAUSE) skip()
                            val parents = ListBuffer[String]()
                            while (nextByte != SELFDEF && nextByte != DEFDEF && nextByte != SPLITCLAUSE) {
                                parents += path()
                            }
                            val body = currentAddr
                            goto(templateEnd)
                            var isTrait = false
                            while (currentAddr.index < definitionEnd.index) {
                                if (nextByte == TRAIT) isTrait = true
                                skip()
                            }
                            if (fullName == target) return Some(((isTrait.toString +: parents.toSeq).toArray, lazyFields(body, templateEnd)))
                            val found = fork(body).scan(templateEnd, fullName, true, target)
                            if (found.nonEmpty) return found
                        }
                        goto(definitionEnd)
                    case _ => skip()
                }
            }
            None
        }
    }
}
