package com.darkmatterservers.eclipsebot.service.config;

import net.dv8tion.jda.api.JDA;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicReference;

@Configuration
public class DiscordBeansConfig {

    /**
     * A mutable holder for JDA to be set after Discord login.
     */
    @Bean
    public AtomicReference<JDA> jdaReference() {
        return new AtomicReference<>();
    }
}
