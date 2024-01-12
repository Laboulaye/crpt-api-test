import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    public static final String CREATE_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final Lock lock = new ReentrantLock();
    private final int requestLimit;
    private final TimeUnit timeUnit;
    private long lastResetTime;
    private int currentRequestsCount;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        this.lastResetTime = System.currentTimeMillis();
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 2);
        Document document = Document.builder()
                .doc_id("1")
                .doc_status("created")
                .doc_type("test_type")
                .build();
        String signature = "test_signature";
        crptApi.createDocument(document, signature);
    }



    //===============================            Private            ===============================//
    //=============================================================================================//

    private void createDocument(Document document, String signature) {
        lock.lock();
        try {
            resetIfNecessary();

            //Wait until a new request is available
            while (currentRequestsCount >= requestLimit) {
                long timeToWait = getTimeToNextReset();
                if (timeToWait > 0) {
                    lock.unlock();
                    Thread.sleep(timeToWait);
                    lock.lock();
                    resetIfNecessary();
                }
            }

            sendCreatingRequest(document, signature);

            currentRequestsCount++;

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Send a post request for creating document
     */
    private void sendCreatingRequest(Document document, String signature) {
        try {
            URL url = new URL(CREATE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Charset", "UTF-8");

            // Convert Document object to JSON string
            String jsonInputString = "{\"document\": " + objectMapper.writeValueAsString(document)
                    + ", \"signature\": " + signature + "}";

            //Write json string to the stream
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            //Get response from the server
            int responseCode = connection.getResponseCode();
            System.out.println("HTTP Response Code: " + responseCode);

            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reset requests counter
     */
    private void resetIfNecessary() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTime >= timeUnit.toMillis(1)) {
            currentRequestsCount = 0;
            lastResetTime = currentTime;
        }
    }

    /**
     * Calculate freeze time for waiting for the next period timeunit
     */
    private long getTimeToNextReset() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastReset = currentTime - lastResetTime;
        return Math.max(0, timeUnit.toMillis(1) - timeSinceLastReset);
    }


    //======================       JSON Document POJO class     =======================================//
    //================================================================================================//
    @Builder
    @Getter
    @Setter
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;

        @Builder
        @Getter
        @Setter
        public static class Description {
            private String participantInn;
        }

        @Builder
        @Getter
        @Setter
        public static class Product {
            private String certificate_document;
            private String certificate_document_date;
            private String certificate_document_number;
            private String owner_inn;
            private String producer_inn;
            private String production_date;
            private String tnved_code;
            private String uit_code;
            private String uitu_code;
        }
    }
}
