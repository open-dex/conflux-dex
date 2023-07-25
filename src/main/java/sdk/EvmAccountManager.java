package sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import conflux.web3j.AccountManager;
import conflux.web3j.types.Address;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;

import java.io.File;
import java.io.IOException;

/**
 * The official account manager only support addresses start with 0x0 or 0x8.
 * This one bypasses the checking logic and then support all addresses.
 */
public class EvmAccountManager extends AccountManager {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String keyfilePrefix = "conflux-keyfile-";
    private static final String keyfileExt = ".json";
    private final int networkId;

    public EvmAccountManager(String dir, int networkId) throws IOException {
        super(dir, networkId);
        this.networkId = networkId;
    }

    protected Address createKeyFile(String password, ECKeyPair ecKeyPair) throws Exception {
        WalletFile walletFile = Wallet.createStandard(password, ecKeyPair);
//        walletFile.setAddress(AddressType.User.normalize(walletFile.getAddress()));
        walletFile.setAddress("0x"+walletFile.getAddress());

        String filename = String.format("%s%s%s", keyfilePrefix, walletFile.getAddress(), keyfileExt);
        File keyfile = new File(this.getDirectory(), filename);
        objectMapper.writeValue(keyfile, walletFile);

        return new Address(walletFile.getAddress(), this.networkId);
    }

    protected String parseAddressFromFilename(String filename) {
        if (!filename.startsWith(keyfilePrefix) || !filename.endsWith(keyfileExt)) {
            return "";
        }

        String hexAddress = filename.substring(keyfilePrefix.length(), filename.length() - keyfileExt.length());

//        try {
//            AddressType.validateHexAddress(hexAddress, AddressType.User);
//        } catch (Exception e) {
//            return "";
//        }

        return hexAddress;
    }

}
