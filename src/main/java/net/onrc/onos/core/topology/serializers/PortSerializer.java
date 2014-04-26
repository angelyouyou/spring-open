package net.onrc.onos.core.topology.serializers;

import java.io.IOException;

import net.onrc.onos.core.topology.Port;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.std.SerializerBase;
import org.openflow.util.HexString;

public class PortSerializer extends SerializerBase<Port> {

    public PortSerializer() {
        super(Port.class);
    }

    @Override
    public void serialize(Port port, JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider)
            throws IOException, JsonProcessingException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("state", "ACTIVE");
        jsonGenerator.writeStringField("dpid", HexString.toHexString(port.getDpid()));
        jsonGenerator.writeNumberField("number", port.getNumber());
        jsonGenerator.writeStringField("desc", port.getDescription());
        jsonGenerator.writeArrayFieldStart("devices");
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }

}
