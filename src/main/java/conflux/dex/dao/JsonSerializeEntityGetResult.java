package conflux.dex.dao;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class JsonSerializeEntityGetResult extends StdSerializer<EntityGetResult<?>> {

	private static final long serialVersionUID = 4666756818382436902L;

	// For auto creation.
    public JsonSerializeEntityGetResult() {
        this(null);
    }
    protected JsonSerializeEntityGetResult(Class<EntityGetResult<?>> t) {
        super(t);
    }

    @Override
    public void serialize(EntityGetResult<?> value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeFieldName("entityGetResult");

        Optional<?> optional = value.get();
        boolean present = optional.isPresent();
        if (!present) {
            gen.writeNull();
        } else {
            gen.writeObject(optional.get());
        }

        gen.writeEndObject();
    }
}
