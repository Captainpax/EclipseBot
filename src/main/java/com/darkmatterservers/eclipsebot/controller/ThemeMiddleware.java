package com.darkmatterservers.eclipsebot.controller;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Middleware for injecting a consistent dark mode theme for HTML views.
 */
@Component
public class ThemeMiddleware implements HandlerInterceptor {

    public static String wrap(String title, String content) {
        return """
        <html lang="en">
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
        """ +
                "<title>" + title + "</title>" +
                """
                    <style>
                      body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background-color: #111;
                        color: #eee;
                        padding: 2rem;
                      }
                      a { color: #58a6ff; text-decoration: none; }
                      a:hover { text-decoration: underline; }
                      input, button {
                        background: #222;
                        border: 1px solid #444;
                        color: #eee;
                        padding: 8px;
                        margin-bottom: 10px;
                        width: 100%;
                        border-radius: 4px;
                      }
                      button {
                        background-color: #2d7d46;
                        border: none;
                        cursor: pointer;
                      }
                      button:hover {
                        background-color: #24913e;
                      }
                      form {
                        max-width: 400px;
                        margin-top: 1rem;
                      }
                      h1, h2, h3 {
                        color: #90caf9;
                      }
                    </style>
                  </head>
                  <body>
                """ + content + """
          </body>
        </html>
        """;
    }

    @Override
    public void postHandle(@NotNull HttpServletRequest request,
                           @NotNull HttpServletResponse response,
                           @NotNull Object handler,
                           ModelAndView modelAndView) {
        // Not used — HTML wrapping handled in controller manually.
    }
}
