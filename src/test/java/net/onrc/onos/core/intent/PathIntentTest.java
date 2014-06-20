package net.onrc.onos.core.intent;

import static org.junit.Assert.assertEquals;
import net.onrc.onos.core.topology.LinkEvent;
import net.onrc.onos.core.util.serializers.KryoFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Unit tests for PathIntent.
 */
public class PathIntentTest {
    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testCreateFirstId() {
        String id = PathIntent.createFirstId("100");
        assertEquals("100___0", id);
    }

    @Test
    public void testCreateNextId() {
        String id = PathIntent.createNextId("100___999");
        assertEquals("100___1000", id);
    }

    @Test
    public void test() {
        KryoFactory factory = new KryoFactory();
        Kryo kryo = factory.newKryo();
        Output output = new Output(1024);

        ConstrainedShortestPathIntent cspIntent1 =
                new ConstrainedShortestPathIntent("1", 2L, 3L, 4L, 5L, 6L, 7L, 1000.0);

        Path path = new Path();
        path.add(new LinkEvent(1L, 1L, 2L, 2L));
        path.add(new LinkEvent(2L, 1L, 3L, 2L));
        path.add(new LinkEvent(3L, 1L, 4L, 2L));

        PathIntent pathIntent1 = new PathIntent("11", path, 123.45, cspIntent1);

        kryo.writeObject(output, pathIntent1);
        output.close();

        Input input = new Input(output.toBytes());

        // create pathIntent from bytes

        PathIntent pathIntent2 =
                kryo.readObject(input, PathIntent.class);
        input.close();

        // check

        assertEquals("11", pathIntent2.getId());
        Path path2 = pathIntent2.getPath();

        assertEquals(Long.valueOf(1L), path2.get(0).getSrc().getDpid());
        assertEquals(Long.valueOf(1L), path2.get(0).getSrc().getNumber());
        assertEquals(Long.valueOf(2L), path2.get(0).getDst().getDpid());
        assertEquals(Long.valueOf(2L), path2.get(0).getDst().getNumber());

        assertEquals(Long.valueOf(2L), path2.get(1).getSrc().getDpid());
        assertEquals(Long.valueOf(1L), path2.get(1).getSrc().getNumber());
        assertEquals(Long.valueOf(3L), path2.get(1).getDst().getDpid());
        assertEquals(Long.valueOf(2L), path2.get(1).getDst().getNumber());

        assertEquals(Long.valueOf(3L), path2.get(2).getSrc().getDpid());
        assertEquals(Long.valueOf(1L), path2.get(2).getSrc().getNumber());
        assertEquals(Long.valueOf(4L), path2.get(2).getDst().getDpid());
        assertEquals(Long.valueOf(2L), path2.get(2).getDst().getNumber());

        assertEquals(123.45, pathIntent2.getBandwidth(), 0.0);

        ConstrainedShortestPathIntent cspIntent2 =
                (ConstrainedShortestPathIntent) pathIntent2.getParentIntent();

        assertEquals("1", cspIntent2.getId());
        assertEquals(2L, cspIntent2.getSrcSwitchDpid());
        assertEquals(3L, cspIntent2.getSrcPortNumber());
        assertEquals(4L, cspIntent2.getSrcMac());
        assertEquals(5L, cspIntent2.getDstSwitchDpid());
        assertEquals(6L, cspIntent2.getDstPortNumber());
        assertEquals(7L, cspIntent2.getDstMac());
        assertEquals(1000.0, cspIntent2.getBandwidth(), 0.0);
    }
}
