package com.exodus.glimpse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Represents a remote monitoring station with API communication methods.
 */
public class RemoteStation {
    private String apiUrl;
    private String apiKey;

    /**
     * Constructor for creating a remote station with API details.
     * @param apiUrl The base URL of the API.
     * @param apiKey The API key for authentication.
     */
    public RemoteStation(String apiUrl, String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    /**
     * Gets top processes from the remote station.
     * @return JSON string of process data.
     * @throws Exception If the API request fails.
     */
    public String getTopProcesses() throws Exception {
        return makeApiRequest("/api/processes");
    }

    /**
     * Gets CPU usage from the remote station.
     * @return JSON string of CPU data.
     * @throws Exception If the API request fails.
     */
    public String getCpuUsage() throws Exception {
        return makeApiRequest("/api/cpu");
    }

    /**
     * Gets memory usage from the remote station.
     * @return JSON string of memory data.
     * @throws Exception If the API request fails.
     */
    public String getMemoryUsage() throws Exception {
        return makeApiRequest("/api/memory");
    }

    /**
     * Gets disk usage from the remote station.
     * @return JSON string of disk data.
     * @throws Exception If the API request fails.
     */
    public String getDiskUsage() throws Exception {
        return makeApiRequest("/api/disk");
    }

    /**
     * Gets network usage from the remote station.
     * @return JSON string of network data.
     * @throws Exception If the API request fails.
     */
    public String getNetworkUsage() throws Exception {
        return makeApiRequest("/api/network");
    }

    /**
     * Makes a generic API request to the remote station.
     * @param endpoint The API endpoint to call.
     * @return JSON response from the API.
     * @throws Exception If the request fails.
     */
    private String makeApiRequest(String endpoint) throws Exception {
        URL url = new URL(apiUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("API request failed with code: " + responseCode);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }
}