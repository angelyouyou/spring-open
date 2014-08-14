package net.onrc.onos.api.newintent;

import org.junit.Test;

import static net.onrc.onos.core.util.ImmutableClassChecker.assertThatClassIsImmutable;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * This class tests the immutability, equality, and non-equality of
 * {@link net.onrc.onos.api.intent.IntentId}.
 */
public class IntentIdTest {
    /**
     * Tests the immutability of {@link net.onrc.onos.api.intent.IntentId}.
     */
    @Test
    public void intentIdFollowsGuidelineForImmutableObject() {
        assertThatClassIsImmutable(IntentId.class);
    }

    /**
     * Tests equality of {@link net.onrc.onos.api.intent.IntentId}.
     */
    @Test
    public void testEquality() {
        IntentId id1 = new IntentId(1L);
        IntentId id2 = new IntentId(1L);

        assertThat(id1, is(id2));
    }

    /**
     * Tests non-equality of {@link net.onrc.onos.api.intent.IntentId}.
     */
    @Test
    public void testNonEquality() {
        IntentId id1 = new IntentId(1L);
        IntentId id2 = new IntentId(2L);

        assertThat(id1, is(not(id2)));
    }

    @Test
    public void valueOf() {
        IntentId id = new IntentId(12345);
        assertEquals("incorrect valueOf", id, IntentId.valueOf("12345"));
    }

    @Test
    public void valueOfHex() {
        IntentId id = new IntentId(0xdeadbeefL);
        assertEquals("incorrect valueOf", id, IntentId.valueOf(id.toString()));
    }

}
