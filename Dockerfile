FROM eclipse-temurin:17-jd
WORKDIR /app
COPY . .
RUN mkdir -p bin &&\
find srs -name "*.java" > sources.txt && \
javac -cp "lib/*" @sources.txt -d bin 
EXPOSE 8080
CMD ["java", "-cp", "bin:lib/*", "com.acadify.MainApplication"]
