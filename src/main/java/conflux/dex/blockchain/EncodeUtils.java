package conflux.dex.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

import conflux.dex.blockchain.crypto.legacy.RpcEncodable;

public class EncodeUtils {
	
	public static String encode(String method, Type<?>... params) {
		Function func = new Function(method, Arrays.asList(params), Collections.emptyList());
		return FunctionEncoder.encode(func);
	}
	
	public static DynamicBytes hex2Bytes(String hex) {
		return new DynamicBytes(Numeric.hexStringToByteArray(hex));
	}
	
	public static DynamicArray<DynamicBytes> hex2BytesArray(List<String> hexs) {
		return new DynamicArray<DynamicBytes>(DynamicBytes.class, hexs.stream()
				.map(EncodeUtils::hex2Bytes)
				.collect(Collectors.toList()));
	}
	
	public static DynamicArray<DynamicBytes> hex2BytesArray(String... hexs) {
		return hex2BytesArray(Arrays.asList(hexs));
	}
	
	public static Bytes32 hex2Bytes32(String hex) {
		return new Bytes32(Numeric.hexStringToByteArray(hex));
	}
	
	public static DynamicArray<Bytes32> hex2Bytes32Array(List<String> hexs) {
		return new DynamicArray<Bytes32>(Bytes32.class, hexs.stream()
				.map(EncodeUtils::hex2Bytes32)
				.collect(Collectors.toList()));
	}
	
	public static DynamicArray<Bytes32> hex2Bytes32Array(String... hexs) {
		return hex2Bytes32Array(Arrays.asList(hexs));
	}
	
	public static DynamicArray<Uint256> nums2Array(List<BigInteger> nums) {
		return new DynamicArray<Uint256>(Uint256.class, nums.stream()
				.map(Uint256::new)
				.collect(Collectors.toList()));
	}
	
	public static DynamicArray<Uint256> nums2Array(BigInteger... nums) {
		return nums2Array(Arrays.asList(nums));
	}
	
	public static DynamicArray<Address> hex2AddressArray(List<String> addresses) {
		return new DynamicArray<Address>(Address.class, addresses.stream()
				.map(Address::new)
				.collect(Collectors.toList()));
	}
	
	public static DynamicArray<Address> hex2AddressArray(String... addresses) {
		return hex2AddressArray(Arrays.asList(addresses));
	}
	
	public static <T extends Type<?> & RpcEncodable> TypedDataDynamicArray<T> typedDatas2Array(List<T> datas) {
		@SuppressWarnings("unchecked")
		Class<T> clz = (Class<T>) datas.get(0).getClass();
		return new TypedDataDynamicArray<T>(clz, datas);
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends Type<?> & RpcEncodable> TypedDataDynamicArray<T> typedDatas2Array(T... datas) {
		return typedDatas2Array(Arrays.asList(datas));
	}

}
