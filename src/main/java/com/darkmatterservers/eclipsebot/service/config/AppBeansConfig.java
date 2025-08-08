package com.darkmatterservers.eclipsebot.service.config;

import com.darkmatterservers.EclipseBytes;
import net.dv8tion.jda.api.JDA;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicReference;

@Configuration
public class AppBeansConfig {

    /**
     * Provides a globally accessible, thread-safe reference to the active JDA instance.
     */
    @Bean
    public AtomicReference<JDA> jdaRef() {
        return new AtomicReference<>();
    }

    /**
     * Bridge to EclipseBytes UI/builders using the shared JDA reference.
     */
    @Bean
    public EclipseBytes eclipseBytes(AtomicReference<JDA> jdaRef) {
        return new EclipseBytes(jdaRef);
    }
}
