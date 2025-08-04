package com.darkmatterservers.eclipsebot.controller;

import com.darkmatterservers.eclipsebot.service.LoggerService;
import com.darkmatterservers.eclipsebot.service.discord.DiscordService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class CoreController {

    private final LoggerService logger;
    private final DiscordService discordService;

    public CoreController(LoggerService logger, DiscordService discordService) {
        this.logger = logger;
        this.discordService = discordService;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String root() {
        logger.info("🌐 GET / requested — responding with homepage", getClass().toString());

        return ThemeMiddleware.wrap("Welcome to EclipseBot", """
            <h1>🌌 EclipseBot Control Panel</h1>
            <p>Welcome! Use the links below to interact:</p>
            <ul>
              <li><a href="/ding">Ping Bot</a></li>
              <li><a href="/status">Check Status</a></li>
              <li><a href="/setup">Setup Discord Bot</a></li>
            </ul>
        """);
    }

    @GetMapping(value = "/ding", produces = MediaType.TEXT_HTML_VALUE)
    public String ding() {
        logger.info("🔄 GET /ding requested — checking bot status", getClass().toString());

        String botName = "Unavailable";
        String botId = "Unavailable";
        String statusMessage;

        try {
            JDA jda = discordService.getJDA();
            if (jda != null && jda.getStatus() == JDA.Status.CONNECTED) {
                SelfUser bot = jda.getSelfUser();
                botName = bot.getName();
                botId = bot.getId();
                statusMessage = "<h2>🔔 Dong!</h2><p>The bot is awake and <strong>connected</strong>.</p>";
            } else {
                statusMessage = "<h2>🔕 Dong?</h2><p>The bot is <strong>not connected</strong>.</p>";
            }
        } catch (Exception e) {
            logger.error("❌ Error during /ding: " + e.getMessage(), getClass().toString(), e);
            statusMessage = "<h2>❌ Dong Failed</h2><p>Could not check bot status.</p>";
        }

        statusMessage += """
        <p><strong>Name:</strong> %s<br><strong>ID:</strong> %s</p>
        <a href="/">Back to home</a>
        """.formatted(botName, botId);

        return ThemeMiddleware.wrap("Ding Response", statusMessage);
    }

    @GetMapping(value = "/status", produces = MediaType.TEXT_HTML_VALUE)
    public String status() {
        logger.info("📈 GET /status requested — returning service status", getClass().toString());

        boolean isOnline = discordService.isRunning();
        JDA jda = discordService.getJDA();

        String statusLabel = isOnline ? "✅ Online" : "❌ Offline";
        String connectionStatus = "Unavailable";
        String botName = "Unavailable";
        String botId = "Unavailable";

        if (jda != null) {
            connectionStatus = jda.getStatus().name();
            try {
                botName = jda.getSelfUser().getName();
                botId = jda.getSelfUser().getId();
            } catch (Exception e) {
                logger.warn("⚠️ Could not get bot identity: " + e.getMessage(), getClass().toString());
            }
        }

        String html = """
        <h2>📊 Bot Status</h2>
        <ul>
          <li><strong>App Status:</strong> %s</li>
          <li><strong>Discord Connection:</strong> %s</li>
          <li><strong>Bot Name:</strong> %s</li>
          <li><strong>Bot ID:</strong> %s</li>
        </ul>
        <a href="/">Back to home</a>
        """.formatted(statusLabel, connectionStatus, botName, botId);

        return ThemeMiddleware.wrap("Bot Status", html);
    }

    @GetMapping(value = "/setup", produces = MediaType.TEXT_HTML_VALUE)
    public String setupForm() {
        return ThemeMiddleware.wrap("EclipseBot Setup", """
            <h2>🔧 Setup EclipseBot</h2>
            <form method="POST" action="/setup">
              <label for="token">Discord Token:</label><br>
              <input type="text" id="token" name="token" required><br><br>

              <label for="botId">Bot ID:</label><br>
              <input type="text" id="botId" name="botId" required><br><br>

              <label for="adminId">Admin Discord ID:</label><br>
              <input type="text" id="adminId" name="adminId" required><br><br>

              <button type="submit">Save and Restart Bot</button>
            </form>
        """);
    }

    @PostMapping(value = "/setup", produces = MediaType.TEXT_HTML_VALUE)
    public String submitSetup(
            @RequestParam String token,
            @RequestParam String botId,
            @RequestParam String adminId
    ) {
        logger.info("🔐 Received bot credentials from setup form", getClass().toString());

        discordService.restartWithToken(token, botId, adminId);

        return ThemeMiddleware.wrap("Restarting...", """
            <h2>♻️ Restarting DiscordService...</h2>
            <p>Please wait while the bot restarts.</p>
            <p>You will be redirected once it is back online.</p>

            <script>
              async function poll() {
                try {
                  const response = await fetch("/status");
                  const text = await response.text();
                  if (text.includes("✅ Online")) {
                    window.location.href = "/status";
                  } else {
                    setTimeout(poll, 2000);
                  }
                } catch {
                  setTimeout(poll, 2000);
                }
              }
              poll();
            </script>
        """);
    }
}
