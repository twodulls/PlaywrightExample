package com.playwright.example.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
public class DataUtils {

    /**
     * UI에서 추출한 텍스트를 검증에 용이한 비교용 포맷('MM.dd')으로 변환합니다.
     * @param dateText 화면에서 가져온 원본 날짜 텍스트 (예: "3월 11일 (수)")
     * @return 비교를 위해 변환된 날짜 문자열 (예: "03.11")
     */
    public static String convertToCompareFormat(String dateText) {
        try {
            // "M월 D일 (W)" -> "M월 D일" 추출
            String datePart = dateText.split("\\(")[0].trim(); // "M월 D일"

            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN);
            MonthDay md = MonthDay.parse(datePart, inputFormatter);

            return String.format("%02d.%02d", md.getMonthValue(), md.getDayOfMonth());
        } catch (Exception e) {
            String fallback = dateText.replaceAll("[^0-9.]", "");
            log.warn(">>> [DataUtils] 날짜 텍스트 변환에 실패했습니다. " +
                    "원본: [{}], 사유: {}, 폴백 처리 결과: [{}]", dateText, e.getMessage(), fallback);
            return fallback;
        }
    }

    /**
     * "14,630" 또는 "33,345원" 같은 텍스트에서 숫자만 추출하여 정수(int)로 반환합니다.
     * @param priceText 화면에서 추출한 원본 금액 텍스트
     * @return 정수형 금액 데이터
     * @throws RuntimeException 변환에 실패하거나 숫자가 없을 경우 명시적 에러 발생 (Fail-Fast)
     */
    public static int parsePrice(String priceText) {
        try {
            // 정규식을 이용해 0~9(숫자)가 아닌 모든 문자 제거
            String numericString = priceText.replaceAll("[^0-9]", "");

            // 숫자가 전혀 없다면 에러 발생
            if (numericString.isEmpty()) {
                throw new NumberFormatException("추출된 숫자가 없습니다.");
            }
            return Integer.parseInt(numericString);

        } catch (Exception e) {
            throw new RuntimeException(">>> [DataUtils] 상품 가격 데이터 변환에 실패했습니다. 원본 텍스트: [" + priceText + "]", e);
        }
    }

    /**
     * 목표 금액을 넘기기 위해 필요한 최소 상품 수량을 계산합니다.
     * @param unitPrice 상품 단가
     * @param targetAmount 도달해야 할 목표 금액 (예: 100,000원)
     * @return 목표 금액을 초과하기 위해 필요한 상품 수량
     */
    public static int calculateRequiredQuantity(int unitPrice, int targetAmount) {
        if (unitPrice <= 0) {
            log.warn(">>> [DataUtils] 상품 단가가 {}원입니다. 상품 가격 설정을 확인해주세요.", unitPrice);
            throw new RuntimeException("상품 단가가 유효하지 않습니다: " + unitPrice + "원");
        }
        return (targetAmount / unitPrice) + 1;
    }
}
