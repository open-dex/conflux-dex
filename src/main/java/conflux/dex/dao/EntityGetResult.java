package conflux.dex.dao;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import conflux.dex.common.BusinessException;
import conflux.dex.common.BusinessFault;

@JsonSerialize(using = JsonSerializeEntityGetResult.class)
public class EntityGetResult<T> {
	
	private T value;
	private BusinessFault notFoundException;
	private Object param;

	private EntityGetResult(T value, BusinessFault notFoundException) {
		this.value = value;
		this.notFoundException = notFoundException;
	}
	
	public static <T> EntityGetResult<T> of(T value) {
		return new EntityGetResult<T>(value, null);
	}
	
	public static <T> EntityGetResult<T> notFound(BusinessFault notFoundException) {
		return new EntityGetResult<T>(null, notFoundException);
	}
	
	public static <T> EntityGetResult<T> ofNullable(T value, BusinessFault notFoundException) {
		if (value == null) {
			return notFound(notFoundException);
		} else {
			return of(value);
		}
	}
	
	public static <T> EntityGetResult<T> of(Optional<T> value, BusinessFault notFoundException) {
		if (value.isPresent()) {
			return of(value.get());
		} else {
			return notFound(notFoundException);
		}
	}
	
	public static <T> EntityGetResult<T> of(List<T> value, BusinessFault notFoundException) {
		if (value.isEmpty()) {
			return notFound(notFoundException);
		} else {
			return ofNullable(value.get(0), notFoundException);
		}
	}

	public EntityGetResult<T> withParam(Object param) {
		this.param = param;
		return this;
	}
	
	public void expectNotFound(BusinessFault alreadyExistsException) throws BusinessException {
		if (this.value != null) {
			throw alreadyExistsException.rise();
		}
	}
	
	public Optional<T> get() {
		return Optional.ofNullable(this.value);
	}
	
	public T mustGet() throws BusinessException {
		if (this.value == null) {
			String message = notFoundException.getMessage();
			if (param != null) {
				message += " " + param.toString();
			}
			throw new BusinessException(notFoundException.getCode(), message);
		} else {
			return this.value;
		}
	}

}
