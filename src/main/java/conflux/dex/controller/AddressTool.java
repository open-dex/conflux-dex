package conflux.dex.controller;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.common.BusinessException;
import conflux.web3j.types.Address;

/**
 * Conflux improves its address algorithm.
 * Use this tool to support HEX(original) address.
 *
 * https://github.com/Conflux-Chain/CIPs/blob/23cdc3e479126d905c77e370321cfa26cca397f9/CIPs/cip-37.md
 */
public class AddressTool {

    public static Address address(String hexOrBase32) {
        if (hexOrBase32 == null) {
            return null;
        }
        if (hexOrBase32.startsWith("0x")) {
            // it's hex
            return new Address(hexOrBase32, getNetId());
        }
        return new Address(hexOrBase32);
    }
    /**
     * This method depends `Domain.defaultChainId`.
     * @param hex
     * @return
     */
    public static String toBase32(String hex) {
        return new Address(hex, getNetId()).getAddress();
    }

    private static int getNetId() {
        return Math.toIntExact(Domain.defaultChainId);
    }

    public static String toHex(String hexOrBase32) {
        if (hexOrBase32 == null || hexOrBase32.isEmpty()) {
            return hexOrBase32;
        }
        if (hexOrBase32.startsWith("0x")) {
            return hexOrBase32;
        } else {
            return decode2hex(hexOrBase32);
        }
    }

    private static String decode2hex(String base32) {
        return Address.decode(base32);
    }

    /**
     * Convert to base32 address if destination is a cfx address.
     * @param destination
     * @return
     */
    public static String convertDestinationBase32(String destination) {
        if (destination == null || destination.length() != 42 || !destination.startsWith("0x")) {
            return destination;
        }
        if (destination.startsWith("0x1") || destination.startsWith("0x8")) {
            return toBase32(destination);
        }
        return destination;
    }
}
