package conflux.dex.blockchain.crypto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import conflux.dex.common.BusinessException;

public class Template {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	public Map<String, List<Entry>> types = new HashMap<String, List<Entry>>();
	public String primaryType;
	public Domain domain;
	public Object message;
	
	public Template(TypedData data) {
		this.types.put(Domain.PRIMARY_TYPE, Domain.SCHEMA);
		this.types.putAll(data.schemas());
		
		this.primaryType = data.primaryType();
		this.domain = data.domain();
		this.message = data;
	}
	
	public String toJson() {
		try {
			return MAPPER.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw BusinessException.internalError("failed to serialize EIP712 template", e);
		}
	}
	
	@Override
	public String toString() {
		return this.toJson();
	}

}
