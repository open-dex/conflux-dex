package conflux.dex.common;

public class BusinessException extends RuntimeException {

	private static final long serialVersionUID = -6157003076026018270L;

	// common error
	public static final int codeInternalError = 1;
	public static final int codeValidateFailed = 2;


	// system internal errors
	public static final int codeSystem = 1000;

	private int code;

	public BusinessException(int code, String message) {
		super(message);

		this.code = code;
	}
	
	public static BusinessException validateFailed(String message, Object... args) {
		return new BusinessException(codeValidateFailed, String.format(message, args));
	}
	
	public static BusinessException internalError() {
		return new BusinessException(codeInternalError, "internal error, please contact the administrator for more details");
	}
	
	public static BusinessException internalError(String message) {
		return new BusinessException(codeInternalError, message);
	}
	
	public static BusinessException internalError(String message, Throwable e) {
		return new BusinessException(codeInternalError, String.format("%s: %s", message, e.getMessage()));
	}
	
	public static BusinessException system(String message) {
		return new BusinessException(codeSystem, message);
	}
	
	public int getCode() {
		return code;
	}
	
	public void setCode(int code) {
		this.code = code;
	}
}
