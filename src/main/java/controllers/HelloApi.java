package controllers;

import java.io.IOException;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HelloApi extends HttpServlet {
    private static final Gson GSON = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        sendMessage(response, "Hello, World!");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String messageParam = "message";
        String message = "";
        try {
            JsonObject requestBody = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            message = requestBody.has(messageParam) && !requestBody.get(messageParam).isJsonNull()
                    ? requestBody.get(messageParam).getAsString()
                    : "";
        } catch (JsonParseException | IllegalStateException exception) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendMessage(response, "Invalid JSON request");
        }
        sendMessage(response, message);
    }

    private static void sendMessage(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(GSON.toJson(Map.of("message", message)));
    }
}