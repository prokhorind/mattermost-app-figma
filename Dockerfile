
# For Java 11, try this
FROM adoptopenjdk/openjdk11

EXPOSE 8080

# Refer to Maven build -> finalName
ARG JAR_FILE

COPY . /opt/app

# cd /opt/app
WORKDIR /opt/app

RUN ./mvnw clean install

# java -jar /opt/app/app.jar
ENTRYPOINT ["java","-jar","./target/figma-0.0.1-SNAPSHOT.jar"]
