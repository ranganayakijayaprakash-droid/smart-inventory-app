import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class Main {

    private static final String DB_URL = "jdbc:sqlite:inventory.db";

    public static void main(String[] args) throws IOException {

        // Create database table
        try (Connection connection = DriverManager.getConnection(DB_URL);
             Statement statement = connection.createStatement()) {

            statement.execute(
                "CREATE TABLE IF NOT EXISTS products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, quantity INTEGER, price REAL)"
            );

        } catch (Exception e) {
            e.printStackTrace();
        }

        HttpServer server =
            HttpServer.create(new InetSocketAddress(8080), 0);

        // Health check API
        server.createContext("/api/health", exchange -> {

            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "*"
            );

            String response =
                "{\"message\":\"Java backend connected!\"}";

            byte[] bytes =
                response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8"
            );

            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        // Products API: GET, POST, DELETE
        server.createContext("/api/products", exchange -> {

            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "*"
            );

            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, DELETE, OPTIONS"
            );

            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type"
            );

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            String response;
            int status = 200;

            try (Connection connection = DriverManager.getConnection(DB_URL)) {

                // ADD PRODUCT
                if (method.equalsIgnoreCase("POST")) {

                    String body = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                    );

                    com.google.gson.JsonObject product =
                        com.google.gson.JsonParser
                            .parseString(body).getAsJsonObject();

                    String name = product.get("name").getAsString();
                    int quantity = product.get("quantity").getAsInt();
                    double price = product.get("price").getAsDouble();

                    try (var statement = connection.prepareStatement(
                        "INSERT INTO products (name, quantity, price) VALUES (?, ?, ?)")) {

                        statement.setString(1, name);
                        statement.setInt(2, quantity);
                        statement.setDouble(3, price);

                        statement.executeUpdate();
                    }

                    response =
                        "{\"message\":\"Product added successfully!\"}";
                }

                // GET ALL PRODUCTS
                else if (method.equalsIgnoreCase("GET")) {

                    com.google.gson.JsonArray products =
                        new com.google.gson.JsonArray();

                    try (var statement = connection.createStatement();
                         var result = statement.executeQuery(
                             "SELECT * FROM products")) {

                        while (result.next()) {

                            com.google.gson.JsonObject product =
                                new com.google.gson.JsonObject();

                            product.addProperty(
                                "id", result.getInt("id")
                            );

                            product.addProperty(
                                "name", result.getString("name")
                            );

                            product.addProperty(
                                "quantity", result.getInt("quantity")
                            );

                            product.addProperty(
                                "price", result.getDouble("price")
                            );

                            products.add(product);
                        }
                    }

                    response = products.toString();
                }

                // DELETE PRODUCT
                else if (method.equalsIgnoreCase("DELETE")) {

                    String[] parts = path.split("/");

                    if (parts.length != 4 ||
                        !parts[3].matches("\\d+")) {

                        status = 400;

                        response =
                            "{\"error\":\"Invalid product ID\"}";

                    } else {

                        int id = Integer.parseInt(parts[3]);

                        try (var statement = connection.prepareStatement(
                            "DELETE FROM products WHERE id = ?")) {

                            statement.setInt(1, id);

                            int deletedRows = statement.executeUpdate();

                            if (deletedRows > 0) {

                                response =
                                    "{\"message\":\"Product deleted successfully!\"}";

                            } else {

                                status = 404;

                                response =
                                    "{\"error\":\"Product not found\"}";
                            }
                        }
                    }
                }

                // UNSUPPORTED METHOD
                else {

                    status = 405;

                    response =
                        "{\"error\":\"Method not supported\"}";
                }

            } catch (Exception e) {

                e.printStackTrace();

                status = 500;

                response =
                    "{\"error\":\"Request failed\"}";
            }

            byte[] bytes =
                response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8"
            );

            exchange.sendResponseHeaders(status, bytes.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });

        server.setExecutor(null);
        server.start();

        System.out.println("Java backend running!");
        System.out.println("Test: http://localhost:8080/api/health");
        System.out.println("Products: http://localhost:8080/api/products");
    }
}