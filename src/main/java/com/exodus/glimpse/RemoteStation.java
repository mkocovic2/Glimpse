package com.exodus.glimpse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RemoteStation {
    private String apiUrl;
    private String apiKey;

    public RemoteStation(String apiUrl, String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    public String getTopProcesses() throws Exception {
        return makeApiRequest("/api/processes");
    }

    public String getCpuUsage() throws Exception {
        return makeApiRequest("/api/cpu");
    }

    public String getMemoryUsage() throws Exception {
        return makeApiRequest("/api/memory");
    }

    public String getDiskUsage() throws Exception {
        return makeApiRequest("/api/disk");
    }

    public String getNetworkUsage() throws Exception {
        return makeApiRequest("/api/network");
    }

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