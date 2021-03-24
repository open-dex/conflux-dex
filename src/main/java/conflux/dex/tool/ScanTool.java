package conflux.dex.tool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import conflux.dex.common.BusinessException;
import conflux.dex.common.Utils;
import conflux.dex.dao.ConfigDao;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanTool {
    public String loadAbi(String jsonFile) {
        InputStream resourceAsStream = ScanTool.class.getResourceAsStream(jsonFile);
        try (Reader reader = new InputStreamReader(resourceAsStream, "UTF-8")) {
            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            return json.getAsJsonArray("abi").toString();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe.getMessage());
        }
    }

    public List<String> listJsonFiles() {
        return Arrays.asList(
                "/blockchain/ERC777.json",
                "/blockchain/FC.json",
                "/blockchain/CRCL.json"
                );
    }
    public String regContractWrap(String addr, String abiType, String name) {
        String jsonFile = "";
        switch (abiType) {
            case "FC": jsonFile = "/blockchain/FC.json"; break;
            case "CRCL": jsonFile = "/blockchain/CRCL.json"; break;
            case "CFX": jsonFile = "/blockchain/WrappedCfx.json"; break;
            default: jsonFile = "/blockchain/ERC777.json"; break;
        }
        ConfigDao configDao = SpringTool.getBean(ConfigDao.class);
        String scanUrl = configDao.getConfig(ConfigDao.KEY_SCAN_URL).orElse("");
        if (scanUrl.isEmpty()) {
            throw BusinessException.system("Scan url not set in DB. "+ConfigDao.KEY_SCAN_URL);
        }
        String scanPwd = configDao.getConfig(ConfigDao.KEY_SCAN_PWD).orElse("");
        if (scanPwd.isEmpty()) {
            throw BusinessException.system("Scan pwd not set in DB. "+ConfigDao.KEY_SCAN_PWD);
        }
        return regContract(addr, jsonFile, scanUrl, scanPwd, name);
    }
    public String regContract(String addr, String jsonFile, String scanUrl, String pwd, String name) {
        RestTemplate t = new RestTemplate();
        String url = scanUrl + "/v1/contract";

        Map<String, String> body = new HashMap<>();
        body.put("address", addr);
        body.put("password", pwd);
        body.put("name", name);
        body.put("website", "");
        body.put("abi", loadAbi(jsonFile));
        body.put("sourceCode", "");
        HttpHeaders headers = new HttpHeaders();
        headers.add("user-agent", "Prevent403");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> request = new HttpEntity<>(Utils.toJson(body), headers);
        ResponseEntity<String> re = t.postForEntity(url, request, String.class);
        return re.getBody();
    }
}
