package conflux.dex.common;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.springframework.core.io.ClassPathResource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import conflux.web3j.RpcException;

public class Utils {
	
	private static final BigDecimal TEN_POW_18 = new BigDecimal(BigInteger.TEN.pow(18));
	
	private static final Gson GSON = new GsonBuilder()
			.setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
			.serializeNulls()
			.create();
	
	public static String getResource(String relativePath) {
    	try {
			return new ClassPathResource(relativePath).getFile().getAbsolutePath();
		} catch (IOException e) {
			String message = String.format("failed to get resource: %s", relativePath);
			throw BusinessException.internalError(message, e);
		}
    }
	
	public static String toJson(Object obj) {
		return GSON.toJson(obj);
	}

	public static <T> T parseJson(String str, Class<T> tClass) {
		return GSON.fromJson(str, tClass);
	}
	
	public static BigDecimal div(BigDecimal dividend, BigDecimal divisor) {
		// hardcoded precision before market-buy precision issue fixed
		return div(dividend, divisor, 18);
	}
	
	public static BigDecimal div(BigDecimal dividend, BigDecimal divisor, int scale) {
		return dividend.divide(divisor, scale, RoundingMode.DOWN).stripTrailingZeros();
	}
	
	public static BigDecimal mul(BigDecimal a, BigDecimal b) {
		// hardcoded precision before market-buy precision issue fixed
		return mul(a, b, 18);
	}
	
	public static BigDecimal mul(BigDecimal a, BigDecimal b, int scale) {
		return a.multiply(b).setScale(scale, RoundingMode.DOWN).stripTrailingZeros();
	}
	
	public static BigInteger toContractValue(BigDecimal value) {
		return value.multiply(TEN_POW_18).toBigInteger();
	}
	
	public static BigDecimal fromContractValue(BigInteger value) {
		return new BigDecimal(value).divide(TEN_POW_18);
	}
	
	public static BigDecimal fromContractValue(BigInteger value, int decimals) {
		return Utils.div(new BigDecimal(value), new BigDecimal(BigInteger.TEN.pow(decimals)));
	}
	
	public static boolean isRpcError(RpcException e) {
		return e.getError() != null
				&& e.getError().getCode() != RpcException.ERROR_IO_SEND.getCode()
				&& e.getError().getCode() != RpcException.ERROR_INTERRUPTED.getCode();
	}

}
