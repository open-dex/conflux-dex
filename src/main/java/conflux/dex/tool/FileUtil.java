package conflux.dex.tool;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class FileUtil {
    /**
     * Generate sql in order to import to database(MariaDB for now).
     * @param resourceName script file name
     * @param placeholder Database name placeholder in script file.
     * @param realName Real database name
     * @return Sql
     * @throws URISyntaxException
     * @throws IOException
     */
    public static String buildTempSql(String resourceName, String placeholder, String realName) throws URISyntaxException, IOException {
        URL from = FileUtil.class.getClassLoader().getResource(resourceName);
        if (from == null) {
            throw new IllegalArgumentException("Resource not found : " + realName);
        }
        List<String> lines = Files.readAllLines(Paths.get(from.toURI()));
        //
        String realLines = lines.stream()
                .map(line -> line.replace(placeholder, realName))
                .collect(Collectors.joining("\n"));
        return realLines;
    }
}
