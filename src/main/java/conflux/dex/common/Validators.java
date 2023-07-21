package conflux.dex.common;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import org.web3j.crypto.Keys;

import conflux.dex.model.Currency;
import conflux.web3j.types.AddressType;

/**
 * Facade to validate user input parameters.
 * 
 * Another way is to use validation API (based on annotations), but the REST controller 
 * requires to inject a BindingResult parameter for validation errors. This way will impact 
 * the REST API documentation generation in some non-annotation based tools (e.g. apiggs)
 */
public class Validators {
	
	private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
	private static final BigInteger SIG_MAX_S = new BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16);
	public static final BigDecimal LIMIT_UINT = BigDecimal.TEN.pow(8);
	public static long defaultTimeDriftMillis = 180000; // 3 minutes

	public static void nonEmpty(String object, String name) {
		nonNull(object, name);
		
		if (object.trim().isEmpty()) {
			throw BusinessException.validateFailed("%s is empty", name);
		}
	}
	
	public static void nonNull(Object object, String name) {
		if (object == null) {
			throw BusinessException.validateFailed("%s not specified", name);
		}
	}

	public static void validateName(String value, int maxLength, String name) {
		if (value == null || value.isEmpty()) {
			throw BusinessException.validateFailed("%s not specified", name);
		}
		
		if (value.length() > maxLength) {
			throw BusinessException.validateFailed("the length of %s is larger than %d", name, maxLength);
		}
	}
	
	public static void validateHexWithPrefix(String prefixedHex) {
		if (prefixedHex == null || prefixedHex.length() < 2) {
			throw BusinessException.validateFailed("HEX prefix 0x missed");
		}
		
		if (prefixedHex.charAt(0) != '0' || prefixedHex.charAt(1) != 'x') {
			throw BusinessException.validateFailed("HEX prefix 0x missed");
		}
		
		for (int i = 2, len = prefixedHex.length(); i < len; i++) {
			char ch = prefixedHex.charAt(i);
			if (ch < '0' || (ch > '9' && ch < 'A') || (ch > 'Z' && ch < 'a') || ch > 'z') {
				throw BusinessException.validateFailed("invalid HEX character");
			}
		}
	}
	
	public static void validateAddress(String address, AddressType expectedType, String name) {
		if (address == null || address.isEmpty()) {
			throw BusinessException.validateFailed("%s not specified", name);
		}

		AddressType.validateHexAddress(address, null);

		// validate checksum of non-lower case address
		if (!address.toLowerCase().equals(address) && !Keys.toChecksumAddress(address).equals(address)) {
			throw BusinessFault.InvalidChecksumAddress.rise();
		}
	}
	
	public static void validateSignature(String signature) {
		if (signature == null || signature.isEmpty()) {
			throw BusinessFault.SignatureMissed.rise();
		}
		
		if (signature.length() != 132) {
			throw BusinessFault.SignatureInvalidLength.rise();
		}
		
		validateHexWithPrefix(signature);
		
		// V is 27 (1B) or 28 (1C)
		if (signature.charAt(130) != '1') {
			throw BusinessException.validateFailed("invalid V in signature");
		}
		
		switch (signature.charAt(131)) {
		case 'b':
		case 'B':
		case 'C':
		case 'c':
			break;
		default:
			throw BusinessException.validateFailed("invalid V in signature");
		}
		
		String s = signature.substring(66, 130);
		if (new BigInteger(s, 16).compareTo(SIG_MAX_S) > 0) {
			throw BusinessException.validateFailed("invalid S in signature");
		}
	}
	
	public static void validatePaging(int offset, int limit, int maxLimit) {
		if (offset < 0) {
			throw BusinessException.validateFailed("offset is less than 0");
		}
		
		if (limit < 1) {
			throw BusinessException.validateFailed("limit should be greater than 0");
		}
		
		if (limit > maxLimit) {
			throw BusinessException.validateFailed("limit is larger than %d", maxLimit);
		}
	}
	
	public static void validateNumber(long num, long min, long max, String name) {
		if (min > max) {
			throw BusinessException.validateFailed("min(%d) is greater than max(%d)", min, max);
		}
		
		if (num < min || num > max) {
			throw BusinessException.validateFailed("%s should be in rage [%d, %d]", name, min, max);
		}
	}
	
	public static void expectPositive(BigDecimal value, String name) {
		if (value == null) {
			throw BusinessException.validateFailed("%s not specified", name);
		}
		
		if (value.compareTo(BigDecimal.ZERO) <= 0) {
			throw BusinessException.validateFailed("%s should be greater than 0", name);
		}
	}
	
	public static void validateAmount(BigDecimal amount, int precision, BigDecimal min, BigDecimal max, String name) {
		if (amount == null) {
			throw BusinessException.validateFailed("%s not specified", name);
		}
		
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw BusinessException.validateFailed("%s should be greater than 0", name);
		}
		
		if (amount.scale() > precision) {
			throw BusinessException.validateFailed("%s's scale should not be greater than %d", name, precision);
		}
		
		if (min != null && amount.compareTo(min) < 0) {
			throw BusinessException.validateFailed("%s should not be less than %s", name, min.toPlainString());
		}
		
		if (max != null && amount.compareTo(max) > 0) {
			throw BusinessException.validateFailed("%s should not be greater than %s", name, max.toPlainString());
		}
	}
	
	public static Instant[] validateTimeRange(long startTimestamp, long endTimestamp, Duration slot, Duration window) {
		Instant max = Instant.now();
		Instant min = max.minus(window);
		
		Instant end = max;
		if (endTimestamp > 0) {
			end = Instant.ofEpochMilli(endTimestamp);
			
			if (end.compareTo(max) > 0) {
				throw BusinessException.validateFailed("end timestamp is too large");
			}
			
			if (end.compareTo(min) < 0) {
				throw BusinessException.validateFailed("end timestamp is too small");
			}
		}
		
		Instant start;
		if (startTimestamp == 0) {
			start = end.minus(slot);
			
			if (start.compareTo(min) < 0) {
				start = min;
			}
		} else {
			start = Instant.ofEpochMilli(startTimestamp);
			
			if (start.compareTo(min) < 0) {
				throw BusinessException.validateFailed("start timestamp is too small");
			}
			
			if (start.compareTo(end) > 0) {
				throw BusinessException.validateFailed("start timestamp should not be greater than end timestamp");
			}
			
			if (start.plus(slot).compareTo(end) < 0) {
				throw BusinessException.validateFailed("time range is too large");
			}
		}
		
		return new Instant[] { start, end };
	}
	
	public static void validateTimestamp(long timestamp) {
		long now = System.currentTimeMillis();
		
		if (timestamp < now - defaultTimeDriftMillis || timestamp > now + defaultTimeDriftMillis) {
			throw BusinessException.validateFailed("invalid time drift (%s)", timestamp - now);
		}
	}
	
	public static void validateExternalChainAddress(String token, String address, boolean isBitcoinTestnet) {
		switch (token) {
		case Currency.BTC:
			if (!isBitcoinAddress(address, isBitcoinTestnet))
				throw BusinessException.validateFailed("invalid bitcoin address");
			break;
		default:
			validateETHAddress(address, "recipient");
			break;
		}
	}
	
	public static boolean isBitcoinAddress(String addr, boolean isTestnet) {
        if (isTestnet) {
        	if (addr.charAt(0) != 'm' && addr.charAt(0) != 'n' && addr.charAt(0) != '2' && !addr.startsWith("tb1"))
        		return false;
        } else {
        	if (addr.charAt(0) != '1' && addr.charAt(0) != '3' && !addr.startsWith("bc1"))
        		return false;
        }
        
        if (addr.length() < 26 || addr.length() > 35)
            return false;
        byte[] decoded = decodeBase58To25Bytes(addr);
        if (decoded == null)
            return false;
 
        byte[] hash1 = sha256(Arrays.copyOfRange(decoded, 0, 21));
        byte[] hash2 = sha256(hash1);
 
        return Arrays.equals(Arrays.copyOfRange(hash2, 0, 4), Arrays.copyOfRange(decoded, 21, 25));
    }
 
    private static byte[] decodeBase58To25Bytes(String input) {
        BigInteger num = BigInteger.ZERO;
        for (char t : input.toCharArray()) {
            int p = ALPHABET.indexOf(t);
            if (p == -1)
                return null;
            num = num.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(p));
        }
 
        byte[] result = new byte[25];
        byte[] numBytes = num.toByteArray();
        System.arraycopy(numBytes, 0, result, result.length - numBytes.length, numBytes.length);
        return result;
    }
 
    private static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(data);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
 
	public static void validateETHAddress(String addr, String name) {
		nonEmpty(addr, name);
		
		String regex = "^0x[0-9a-fA-F]{40}$";
		if (!addr.matches(regex)) {
			throw BusinessException.validateFailed("%s is invalid ethereum address", name);
		}
	}

}
