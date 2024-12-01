package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;

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
            exchange.getResponseHeaders().add("Content-Type", Files.probeContentType(file.toPath())); //Either html or mp4
            exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + file.length()); //Range which bytes are sent
            exchange.getResponseHeaders().add("Accept-Ranges", "bytes"); //Could be necessary to inform the browser that the server is capable of byte-ranges (Chrome works without it as well)
            exchange.sendResponseHeaders(206, length); // Partial content

            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 OutputStream os = exchange.getResponseBody()) {
                raf.seek(start);
                byte[] buffer = new byte[8192];
                long bytesRead = 0;
                while (bytesRead < length) {
                    int read = raf.read(buffer, 0, (int) Math.min(buffer.length, length - bytesRead));
                    if (read == -1) break;
                    os.write(buffer, 0, read);
                    bytesRead += read;
                }
            }
        }
    }
}

