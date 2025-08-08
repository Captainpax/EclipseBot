# Start from an official lightweight JDK image
FROM eclipse-temurin:21-jdk-alpine

# Set the working directory
WORKDIR /app

# Copy the jar file into the container
COPY build/libs/*.jar app.jar

# Expose ports 5000-5100 for bot operations
EXPOSE 5000-5100

# Mount the Docker socket for in-container Docker control
VOLUME /var/run/docker.sock

# Add a simple health check hitting localhost
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:5000 || exit 1

# Run the jar file
ENTRYPOINT ["java", "-jar", "EclipseBot-0.2.4.jar"]
