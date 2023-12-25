# Use the official OpenJDK base image
FROM openjdk:19

# Set the working directory
WORKDIR /app

# Copy the application JAR file into the container
COPY target/tz-1.0-SNAPSHOT-jar-with-dependencies.jar /app/tz-1.0-SNAPSHOT-jar-with-dependencies.jar

# Specify the default command to run when the container starts
ENTRYPOINT ["java", "-jar", "tz-1.0-SNAPSHOT-jar-with-dependencies.jar"]