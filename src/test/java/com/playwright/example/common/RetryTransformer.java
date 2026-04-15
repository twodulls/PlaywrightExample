package com.playwright.example.common;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 모든 테스트 메서드에 RetryAnalyzer를 전역으로 적용합니다.
 * suite.xml의 <listeners>에 등록하여 사용합니다.
 */
public class RetryTransformer implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor testConstructor, Method testMethod) {
        // 모든 @Test 메서드에 RetryAnalyzer를 자동으로 주입합니다.
        annotation.setRetryAnalyzer(RetryAnalyzer.class);
    }
}