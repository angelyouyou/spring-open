package net.onrc.onos.core.intent;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Unit tests for ShortestPathIntent.
 */
public class ShortestPathIntentTest {
    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void test() {
        Kryo kryo = new Kryo();
        Output output = new Output(1024);

        ShortestPathIntent intent1 =
                new ShortestPathIntent("1", 2L, 3L, 4L, 5L, 6L, 7L);

        kryo.writeObject(output, intent1);
        output.close();

        Input input = new Input(output.toBytes());
        ShortestPathIntent intent2 =
                kryo.readObject(input, ShortestPathIntent.class);
        input.close();

        assertEquals("1", intent2.getId());
        assertEquals(2L, intent2.getSrcSwitchDpid());
        assertEquals(3L, intent2.getSrcPortNumber());
        assertEquals(4L, intent2.getSrcMac());
        assertEquals(5L, intent2.getDstSwitchDpid());
        assertEquals(6L, intent2.getDstPortNumber());
        assertEquals(7L, intent2.getDstMac());
    }
}
