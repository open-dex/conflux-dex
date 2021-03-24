package conflux.dex.controller;

import java.net.SocketTimeoutException;

import javax.servlet.ServletException;

import conflux.web3j.RpcException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import conflux.dex.common.BusinessException;
import conflux.web3j.types.AddressException;

@RestControllerAdvice
public class ResponseAdvice implements ResponseBodyAdvice<Object> {
	private static final Logger logger = LoggerFactory.getLogger(ResponseAdvice.class);
	private ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
			ServerHttpResponse response) {
		if (response instanceof ServletServerHttpResponse) {
			ServletServerHttpResponse sshr = (ServletServerHttpResponse) response;
			if (sshr.getServletResponse().getStatus() != HttpStatus.OK.value()) {
				return body;
			}
		}
		
		if (body instanceof Response) {
			return body;
		}
		
		Response data = Response.success(body);
		if (body == null && returnType.getParameterType().equals(String.class)) {
			// return type is String and value is null, goes here.
		} else if (!(body instanceof String)) {
			return data;
		}
		
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		
		try {
			return objectMapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			logger.error("failed to convert string to JSON format", e);
			return Response.failure(BusinessException.internalError());
		}
	}
	
	@ExceptionHandler(ServletException.class)
	public Response handleServletExceptionException(ServletException e) {
		logger.debug("ServletException occurred", e);
		return Response.failure(e.getMessage(), null);
	}
	
	@ExceptionHandler(TypeMismatchException.class)
	public Response handleTypeMismatchException(TypeMismatchException e) {
		logger.debug("TypeMismatchException occurred", e);
		return Response.failure(e.getMessage(), null);
	}
	
	@ExceptionHandler(HttpMessageConversionException.class)
	public Response handleHttpMessageConversionException(HttpMessageConversionException e) {
		logger.error("HttpMessageConversionException occurred", e);
		return Response.failure(e.getMessage(), null);
	}
	
	@ExceptionHandler(ClientAbortException.class)
	public Response handleClientAbortException(ClientAbortException e) {
		logger.debug("ClientAbortException occurred", e);
		return Response.failure(e.getMessage(), null);
	}
	
	@ExceptionHandler(SocketTimeoutException.class)
	public Response handleSocketTimeoutException(SocketTimeoutException e) {
		logger.debug("SocketTimeoutException occurred", e);
		return Response.failure(e.getMessage(), null);
	}
	
	@ExceptionHandler(BusinessException.class)
	public Response handleBusinessException(BusinessException e) {
		if (logger.isDebugEnabled()) {
			String message = String.format("business exception occurred, code = %d, message = %s", e.getCode(), e.getMessage());
			logger.debug(message, e);
		}
		return Response.failure(e);
	}
	
	@ExceptionHandler(AddressException.class)
	public Response handleAddressException(AddressException e) {
		logger.debug("AddressException occurred", e);
		return Response.failure(e.getMessage(), BusinessException.codeValidateFailed);
	}

	@ExceptionHandler(RpcException.class)
	public Response handleRpcException(RpcException e) {
		logger.debug("unexpected rpc exception occurred", e);
		return Response.failure(e.getMessage(), BusinessException.codeSystem);
	}

	@ExceptionHandler(DataAccessException.class)
	public Response handleDataAccessException(DataAccessException e) {
		logger.error("unexpected dao exception occurred", e);
		return Response.failure(BusinessException.internalError());
	}
	
	@ExceptionHandler(Exception.class)
	public Response handleException(Exception e) {
		logger.error("unexpected exception occurred", e);
		return Response.failure(BusinessException.internalError());
	}

}
