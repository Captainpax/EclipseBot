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

    /**
     * Exposes the current JDA instance as a bean after it's been initialized.
     * Spring will attempt to inject this only when it's available.
     */
    @Bean
    public JDA jda(AtomicReference<JDA> jdaReference) {
        JDA jda = jdaReference.get();
        if (jda == null) {
            throw new IllegalStateException("JDA instance has not been initialized yet.");
        }
        return jda;
    }
}
