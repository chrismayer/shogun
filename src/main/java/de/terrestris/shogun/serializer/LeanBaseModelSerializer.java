package de.terrestris.shogun.serializer;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

import de.terrestris.shogun.model.BaseModelInterface;

/**
 * A serializer that can handle java.util.Date-instances.
 * 
 * @author terrestris GmbH & Co. KG
 * 
 */
public class LeanBaseModelSerializer extends JsonSerializer<Set> {

	@Override
	public void serialize(Set value, JsonGenerator jgen,
			SerializerProvider provider) throws IOException,
			JsonProcessingException {
		
		jgen.writeStartArray();
		for (Iterator iterator = value.iterator(); iterator.hasNext();) {
			BaseModelInterface object = (BaseModelInterface) iterator.next();
			jgen.writeNumber(object.getId());
		}
		jgen.writeEndArray();
	}
    
}
