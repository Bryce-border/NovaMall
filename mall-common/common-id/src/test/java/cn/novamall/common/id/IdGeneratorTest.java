package cn.novamall.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdGeneratorTest {

    @Test
    void generatesIncreasingUniqueIds() {
        Set<Long> ids = new HashSet<>();
        long previous = -1;
        for (int i = 0; i < 5000; i++) {
            long current = IdGenerator.nextId();
            assertTrue(current > previous);
            assertTrue(ids.add(current));
            previous = current;
        }
        assertEquals(5000, ids.size());
    }
}
