package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;


public class HTTPServer {


    public static void main(String[] args) throws IOException {
        // Create an HTTP server that listens on port 8000
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new FileHandler());
        server.setExecutor(null); // Default executor
        server.start();
        System.out.println("Server started at http://localhost:8000");
    }

    static class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String filePath = "video_website" + exchange.getRequestURI().getPath();
            File file = new File(filePath);

            if (!file.exists() || file.isDirectory()) { //If no file is on the server
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            if (rangeHeader == null) {
                //For index.html
                serveFile(exchange, file, 0, file.length() - 1);
            } else {
                //For video
                long fileLength = file.length();
                String[] range = rangeHeader.replace("bytes=", "").split("-");
                long start = Long.parseLong(range[0]);
                long end = range.length > 1 ? Long.parseLong(range[1]) : fileLength - 1;
                serveFile(exchange, file, start, end);
            }
        }

        private void serveFile(HttpExchange exchange, File file, long start, long end) throws IOException {
            long length = end - start + 1;

            exchange.getResponseHeaders().add("Content-Type", determineContentType(file));
            exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + file.length()); //Range which bytes are sent
            exchange.getResponseHeaders().add("Accept-Ranges", "bytes"); //Could be necessary to inform the browser that the server is capable of byte-ranges (Chrome works without it as well)
            exchange.sendResponseHeaders(206, length); // Partial content

            int bufferingEvents = 0;

            ArrayList<Long> bufferingDurations = new ArrayList<>();
            ArrayList<Long> Timestamps = new ArrayList<>();
            ArrayList<Long> bytesReadList = new ArrayList<>();

            ArrayList<Long> bufferingDurationsWrite = new ArrayList<>();
            ArrayList<Long> bufferingDurationsRead = new ArrayList<>();


            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 OutputStream os = exchange.getResponseBody()) {
                System.out.println("Hello");
                raf.seek(start);
                byte[] buffer = new byte[8192];
                long startTime = System.nanoTime();

                long bytesRead = 0;
                while (bytesRead < length) {
                    long startBufferingTime = System.nanoTime();
                    int read = raf.read(buffer, 0, (int) Math.min(buffer.length, length - bytesRead));
                    long finishBufferingTimeRead = System.nanoTime();
                    if (read == -1) break;
                    long startBufferingTimeWrite = System.nanoTime();
                    os.write(buffer, 0, read);
                    long finishBufferingTime = System.nanoTime();
                    bufferingDurations.add(finishBufferingTime - startBufferingTime);
                    bufferingDurationsWrite.add(finishBufferingTime - startBufferingTimeWrite);
                    bufferingDurationsRead.add(finishBufferingTimeRead - startBufferingTime);
                    bufferingEvents += 1;

                    bytesRead += read;

                    Timestamps.add(finishBufferingTime - startTime);
                    bytesReadList.add(bytesRead);
                }
                long finishTime = System.nanoTime();
                long timeElapsed = finishTime - startTime;

                System.out.println("Time elapsed (ns): " + timeElapsed);
                System.out.println("Number of buffering events: " + bufferingEvents);

                System.out.println(bufferingDurations);
                System.out.println(Timestamps);
                System.out.println(bytesReadList);

                System.out.println(bufferingDurationsWrite);
                System.out.println(bufferingDurationsRead);

            }
        }


    }

    private static String determineContentType(File file) throws IOException {
        String ctype = null;
        if ((ctype = Files.probeContentType(file.toPath())) != null) {
            return ctype;
        } else {
            String filename = file.getName();
            if (filename.endsWith(".mpd")) return "application/dash+xml";
            if (filename.endsWith(".m4s")) return "video/mp4";
            if (filename.endsWith(".mp4")) return "video/mp4";
        }
        return "application/octet-stream"; // Default type
    }

}