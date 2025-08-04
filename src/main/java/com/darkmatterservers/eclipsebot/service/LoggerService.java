package com.darkmatterservers.eclipsebot.service;

import com.darkmatterservers.eclipsebot.service.config.YamlService;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class LoggerService {

    private final YamlService yamlService;
    private JDA jda;

    public LoggerService(@Lazy YamlService yamlService) {
        this.yamlService = yamlService;
    }

    @PostConstruct
    public void init() {
        info("🔧 LoggerService initialized", getClass().toString());
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    public void info(String msg, String source) {
        log("INFO", msg, source, null);
    }

    public void warn(String msg, String source) {
        log("WARN", msg, source, null);
    }

    public void error(String msg, String source, Throwable t) {
        log("ERROR", msg, source, t);
    }

    public void success(String msg, String source) {
        log("SUCCESS", msg, source, null);
    }

    private void log(String level, String msg, String source, Throwable t) {
        System.out.printf("[%s] %s — %s%n", level, source, msg);
        if (t != null) t.printStackTrace();

        // Optional: mirror to Discord log channel if JDA is ready
        if (jda != null && yamlService != null) {
            String channelId = yamlService.getString("logChannelId");
            if (channelId != null) {
                MessageChannel channel = jda.getTextChannelById(channelId);
                if (channel != null) {
                    channel.sendMessage(String.format("`[%s]` %s", level, msg)).queue();
                }
            }
        }
    }
}
