package com.example.aqi_backend.controller;

import com.example.aqi_backend.service.AqiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MapController {

    private final AqiService aqiService;

    public MapController(AqiService aqiService) {
        this.aqiService = aqiService;
    }

    private static final Object[][] CITIES = {
        // North India
        {28.6139, 77.2090, "Delhi"},
        {28.4595, 77.0266, "Gurugram"},
        {28.7041, 77.1025, "Delhi North"},
        {26.8467, 80.9462, "Lucknow"},
        {25.3176, 82.9739, "Varanasi"},
        {26.9124, 75.7873, "Jaipur"},
        {30.7333, 76.7794, "Chandigarh"},
        {27.1767, 78.0081, "Agra"},
        {24.5854, 73.7125, "Udaipur"},
        {27.8974, 78.0880, "Aligarh"},
        {26.4499, 80.3319, "Kanpur"},
        {29.9457, 78.1642, "Haridwar"},
        {30.3165, 78.0322, "Dehradun"},
        {31.1048, 77.1734, "Shimla"},
        {32.7266, 74.8570, "Jammu"},
        {28.0229, 73.3119, "Bikaner"},
        {25.1478, 75.8480, "Kota"},
        {29.3909, 76.9635, "Panipat"},
        // West India
        {19.0760, 72.8777, "Mumbai"},
        {23.0225, 72.5714, "Ahmedabad"},
        {18.5204, 73.8567, "Pune"},
        {21.1458, 79.0882, "Nagpur"},
        {21.1702, 72.8311, "Surat"},
        {22.3072, 73.1812, "Vadodara"},
        {20.0059, 73.7797, "Nashik"},
        {19.9975, 72.9189, "Bhiwandi"},
        {22.9734, 78.6569, "Bhopal"},
        {22.7196, 75.8577, "Indore"},
        {21.7051, 72.9959, "Bhavnagar"},
        {23.2599, 77.4126, "Bhopal East"},
        // East India
        {22.5726, 88.3639, "Kolkata"},
        {20.2961, 85.8245, "Bhubaneswar"},
        {23.3441, 85.3096, "Ranchi"},
        {25.5941, 85.1376, "Patna"},
        {26.1445, 91.7362, "Guwahati"},
        {22.8046, 86.2029, "Jamshedpur"},
        {23.6693, 86.1511, "Dhanbad"},
        {21.2514, 81.6296, "Raipur"},
        // South India
        {13.0827, 80.2707, "Chennai"},
        {12.9716, 77.5946, "Bengaluru"},
        {17.3850, 78.4867, "Hyderabad"},
        {11.0168, 76.9558, "Coimbatore"},
        {9.9252,  78.1198, "Madurai"},
        {10.7905, 78.7047, "Tiruchirappalli"},
        {13.6288, 79.4192, "Tirupati"},
        {16.3067, 80.4365, "Vijayawada"},
        {17.6868, 83.2185, "Visakhapatnam"},
        {15.3173, 75.7139, "Hubli"},
        {12.2958, 76.6394, "Mysuru"},
        {11.6643, 78.1460, "Salem"},
        // Kerala
        {9.9312,  76.2673, "Kochi"},
        {8.5241,  76.9366, "Thiruvananthapuram"},
        {11.2588, 75.7804, "Kozhikode"},
        {8.8932,  76.6141, "Kollam"},
        {10.5276, 76.2144, "Thrissur"},
        {11.8745, 75.3704, "Kannur"},
        {9.5916,  76.5222, "Kottayam"},
        {10.0004, 76.3637, "Aluva"},
        {9.1858,  76.5164, "Pathanamthitta"},
        {11.5854, 76.0845, "Malappuram"},
    };

    @GetMapping("/aqi/map")
    public ResponseEntity<?> getMapData() {
        List<Map<String, Object>> cityData = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();

        for (Object[] cityInfo : CITIES) {
            futures.add(executor.submit(() -> {
                try {
                    double lat  = (double) cityInfo[0];
                    double lon  = (double) cityInfo[1];
                    String name = (String) cityInfo[2];

                    Map<String, Object> data = aqiService.getAqiByCoords(lat, lon);

                    Map<String, Object> city = new HashMap<>();
                    city.put("name", name);
                    city.put("lat",  lat);
                    city.put("lon",  lon);
                    city.put("aqi",  data.get("aqi"));
                    city.put("pm25", data.get("pm25"));
                    city.put("pm10", data.get("pm10"));
                    city.put("temp", data.get("temperature"));
                    cityData.add(city);
                    System.out.println("✓ " + name + " AQI=" + data.get("aqi"));
                } catch (Exception e) {
                    System.out.println("✗ " + cityInfo[2] + ": " + e.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(20, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        System.out.println("Map loaded: " + cityData.size() + "/" + CITIES.length + " cities");
        return ResponseEntity.ok(cityData);
    }
}
