/*
 * The MIT License
 *
 * Copyright 2026 Slam.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package netwatchdog;

/**
 *
 * @author Slam
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import java.util.regex.Pattern;

public class OoklaServerClient {

    private static final String SERVERS_URL = "https://www.speedtest.net/api/js/servers?engine=js&limit=20&https_functional=true";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    public List<SpeedTestServer> obtenerServidores() throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(SERVERS_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", "NetWatchDog/1.0");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Ookla HTTP ERROR: " + responseCode);
            }

            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
            }

            return parseServers(json.toString());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private List<SpeedTestServer> parseServers(String json) throws IOException {
        List<SpeedTestServer> servers = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{([^{}]*)\\}");
        Matcher objectMatcher = objectPattern.matcher(json);

        while (objectMatcher.find()) {
            String object = objectMatcher.group(1);
            try {
                int id = Integer.parseInt(getString(object, "id"));
                String host = getString(object, "host");

                // Aplicamos decodeUnicode para corregir texto como "M\u00e9xico"
                String name = decodeUnicode(getString(object, "name"));
                String country = decodeUnicode(getString(object, "country"));
                String sponsor = decodeUnicode(getString(object, "sponsor"));

                double latitude = Double.parseDouble(getString(object, "lat"));
                double longitude = Double.parseDouble(getString(object, "lon"));
                double distance = Double.parseDouble(getString(object, "distance"));

                servers.add(new SpeedTestServer(id, host, name, country, sponsor, latitude, longitude, distance));
            } catch (Exception ex) {
                // Ignorar objetos incompletos
            }
        }

        if (servers.isEmpty()) {
            throw new IOException("Ookla no devolvió servidores válidos.");
        }

        return servers;
    }

    private String getString(String json, String field) throws IOException {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}]+))");
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            throw new IOException("Campo inexistente: " + field);
        }

        if (matcher.group(1) != null) {
            return matcher.group(1);
        }

        return matcher.group(2).trim();
    }

    /**
     * Convierte secuencias tipo \u00e9 a caracteres UTF-8.
     */
    private String decodeUnicode(String input) {
        if (input == null || !input.contains("\\u")) {
            return input;
        }
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            char ch = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
