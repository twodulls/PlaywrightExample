package com.playwright.example.testdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;

public class TestDataManager {
    private static TestDataManager instance;
    private final TestData testData;

    private TestDataManager(String env){
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String path = String.format("yml-%s/testdata.yml", env);
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("File not found in classpath: " + path);
            }
            this.testData = mapper.readValue(input, TestData.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load or parse " + path, e);
        }
    }

    public static synchronized TestDataManager getInstance(String env) {
        if (instance == null) {
            instance = new TestDataManager(env);
        }
        return instance;
    }

    public TestData getData() {
        return testData;
    }
}
