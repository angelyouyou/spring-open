package net.onrc.onos.core.topology.web.serializers;

import java.io.IOException;

import net.onrc.onos.core.topology.Link;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.std.SerializerBase;
import org.openflow.util.HexString;

public class LinkSerializer extends SerializerBase<Link> {

    public LinkSerializer() {
        super(Link.class);
    }

    @Override
    public void serialize(Link link, JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("src-switch",
                HexString.toHexString(link.getSrcSwitch().getDpid()));
        jsonGenerator.writeNumberField("src-port",
                link.getSrcPort().getNumber());
        jsonGenerator.writeStringField("dst-switch",
                HexString.toHexString(link.getDstSwitch().getDpid()));
        jsonGenerator.writeNumberField("dst-port",
                link.getDstPort().getNumber());
        jsonGenerator.writeEndObject();
    }

}
