package conflux.dex.controller;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import conflux.dex.common.BusinessException;
import conflux.dex.common.BusinessFault;

/**
 * A uniform response data structure for REST API.
 */
public class Response {
	private static final ObjectMapper mapper = new ObjectMapper();
	
	private boolean success;
	private String message;
	private Object data;
	
	public static Response success(Object data) {
		Response response = new Response();
		response.success = true;
		response.message = "success";
		response.data = data;
		return response;
	}
	
	public static Response failure(String message, Object data) {
		Response response = new Response();
		response.message = message;
		response.data = data;
		return response;
	}
	
	public static Response failure(BusinessException e) {
		Response response = new Response();
		response.message = e.getMessage();
		response.data = e.getCode();
		return response;
	}
	
	public boolean isSuccess() {
		return success;
	}
	
	public void setSuccess(boolean success) {
		this.success = success;
	}
	
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
	
	public Object getData() {
		return data;
	}
	
	public void setData(Object data) {
		this.data = data;
	}
	
	@Override
	public String toString() {
		return String.format("Response{success=%s, message=%s, data=%s}", this.success, this.message, this.data);
	}
	
	public void ensureSucceeded() {
		if (!this.success) {
			throw new RuntimeException(String.format("request failed, message = %s, data = %s", this.message, this.data));
		}
	}
	
	public <T> T getValue(Class<T> type) {
		this.ensureSucceeded();
		
		return mapper.convertValue(this.data, type);
	}
	
	public <T> Optional<T> getEntity(Class<T> type, BusinessFault... notFoundExceptions) {
		if (!this.success && this.data != null && notFoundExceptions != null) {
			for (BusinessFault e : notFoundExceptions) {
				if (this.data.equals(e.getCode())) {
					return Optional.empty();
				}
			}
		}
		
		return Optional.of(this.getValue(type));
	}
}
