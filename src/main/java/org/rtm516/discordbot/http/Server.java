/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/GeyserDiscordBot
 */

package org.rtm516.discordbot.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.rtm516.discordbot.util.SponsorUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Server {
    private final HttpServer server;

    public Server() throws Exception {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", 3000), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getQuery() == null) {
                respond("Invalid request", exchange);
                return;
            }

            Map<String, String> queryParams = queryToMap(exchange.getRequestURI().getQuery());

            String code = queryParams.getOrDefault("code", null);
            String state = queryParams.getOrDefault("state", null);

            if (code == null || state == null) {
                respond("Invalid request", exchange);
            } else {
                respond(SponsorUtil.linkGithub(UUID.fromString(state), code), exchange);
            }
        });
        server.setExecutor(null); // creates a default executor
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void respond(String message, HttpExchange exchange) throws IOException {
        String response = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset='utf-8'>
                  <meta http-equiv='X-UA-Compatible' content='IE=edge'>
                  <title>Sponsor Verification</title>
                  <meta name='viewport' content='width=device-width, initial-scale=1'>
                  <style>
                    body {
                      margin: 0;
                      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
                        'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
                        sans-serif;
                      -webkit-font-smoothing: antialiased;
                      -moz-osx-font-smoothing: grayscale;
                      display: flex;
                      justify-content: center;
                    }
                  </style>
                </head>
                <body>
                    <h1>%s</h1>
                </body>
                </html>""".formatted(message);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    /**
     * Take a raw query string and transforming it into a key value map
     *
     * @param query The raw query string
     * @return The key value map of the query params
     */
    public static Map<String, String> queryToMap(String query) {
        if(query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            param = URLDecoder.decode(param, StandardCharsets.UTF_8);
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
