package codechicken.multipart.asm;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TastyReaderTest {
    @Test
    void readsTraitFlagAndDirectParents() throws Exception {
        Path file = Path.of(ReaderChild.class.getResource("ReaderChild.tasty").toURI());
        String[] info = TastyReader.read(Files.readAllBytes(file), ReaderChild.class.getName());
        assertEquals("true", info[0]);
        assertTrue(List.of(info).contains(ReaderParent.class.getName()));
    }

    @Test
    void distinguishesConcreteSuperclass() throws Exception {
        Path file = Path.of(ReaderBase.class.getResource("ReaderBase.tasty").toURI());
        String[] info = TastyReader.read(Files.readAllBytes(file), ReaderBase.class.getName());
        assertEquals("false", info[0]);
    }

    @Test
    void readsAppliedParentWithoutResolvingClientMethodBodies() throws Exception {
        Path file = Path.of(ReaderApplied.class.getResource("ReaderApplied.tasty").toURI());
        String[] info = TastyReader.read(Files.readAllBytes(file), ReaderApplied.class.getName());
        assertEquals("false", info[0]);
        assertTrue(List.of(info).contains(ReaderGeneric.class.getName()));
    }
}
