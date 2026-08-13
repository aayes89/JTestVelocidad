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
public class SpeedTestServer {

    private final int id;
    private final String host;
    private final String name;
    private final String country;
    private final String sponsor;
    private final double latitude;
    private final double longitude;
    private final double distance;

    public SpeedTestServer(int id, String host, String name, String country, String sponsor, double latitude, double longitude, double distance) {
        this.id = id;
        this.host = host.replaceAll("^https?://", "").replaceAll("/.*$", "");
        this.name = name;
        this.country = country;
        this.sponsor = sponsor;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distance = distance;
    }

    public int getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public String getSponsor() {
        return sponsor;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getDistance() {
        return distance;
    }

    @Override
    public String toString() {
        return String.format("#%d %s - %s (%.1f km) [%s]", id, name, sponsor, distance, host);
    }
}
