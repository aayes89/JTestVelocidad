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
import java.util.regex.Matcher;

import java.util.regex.Pattern;

public class GeoLocator {

    private static final String GEO_URL = "http://ip-api.com/json/?fields=status,message,query,lat,lon,city,country";
    private static final int TIMEOUT = 5000;

    public static NetworkLocation locate() throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GEO_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("GeoIP HTTP ERROR: " + responseCode);
            }

            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
            }
            return parse(json.toString());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static NetworkLocation parse(String json) throws IOException {
        String status = extractString(json, "status");
        if (!"success".equals(status)) {
            String message = extractString(json, "message");
            throw new IOException("GeoIP ERROR: " + message);
        }

        String ip = extractString(json, "query");
        String country = extractString(json, "country");
        String city = extractString(json, "city");
        double lat = extractDouble(json, "lat");
        double lon = extractDouble(json, "lon");

        return new NetworkLocation(ip, country, city, lat, lon);
    }

    private static String extractString(String json, String field) throws IOException {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IOException("Campo JSON inexistente: " + field);
        }
        return matcher.group(1);
    }

    private static double extractDouble(String json, String field) throws IOException {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?[0-9.]+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IOException("Campo JSON inexistente: " + field);
        }
        return Double.parseDouble(matcher.group(1));
    }
}
