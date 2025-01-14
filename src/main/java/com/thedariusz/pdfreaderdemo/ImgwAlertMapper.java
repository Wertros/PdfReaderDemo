package com.thedariusz.pdfreaderdemo;

import com.thedariusz.pdfreaderdemo.model.ImgwLocalMeteoAlert;
import com.thedariusz.pdfreaderdemo.model.ImgwLocalMeteoAlert.AlertStatus;
import com.thedariusz.pdfreaderdemo.model.ImgwMeteoAlert;
import com.thedariusz.pdfreaderdemo.model.Voivodeship;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ImgwAlertMapper {

    public static final int NUMBER_NOT_FOUND = -1;
    private static final String EMPTY_STRING = "";
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("HH:mm 'dnia' dd.MM.yyyy");
    public static final LocalDateTime DEFAULT_EMPTY_DATE = LocalDateTime.of(1900, 1, 1, 0, 0);
    public static final String UNSPECIFIED = "unspecified";
    public static final LocalDateTime DATETIME_NOW = LocalDateTime.now();

    public ImgwMeteoAlert toModel(String fetchedTextFromPdf) {

        String[] pdfTextSplitToSpecificAlerts = fetchedTextFromPdf.split(TextPatterns.PDF_TEXT_ALERTS_SPLITTER);

        if (pdfTextSplitToSpecificAlerts.length < 2) {
            throw new IllegalArgumentException("Problem with splitting text from pdf to fetch specific alerts");
        }

        String headerOfLocalAlerts = pdfTextSplitToSpecificAlerts[0];

        String[] textBodyOfLocalAlerts = Arrays.copyOfRange(pdfTextSplitToSpecificAlerts, 1, pdfTextSplitToSpecificAlerts.length);
        List<ImgwLocalMeteoAlert> mappedImgwLocalMeteoAlerts = new ArrayList<>();
        Arrays.stream(textBodyOfLocalAlerts)
                .forEach(localAlertText -> {
                    List<LocalDateTime> startAndStopAlertDates = getAlertStartAndStopDate(localAlertText);
                    mappedImgwLocalMeteoAlerts.add(
                            new ImgwLocalMeteoAlert(
                                    getAlertType(localAlertText),
                                    getAlertDegree(localAlertText),
                                    getAlertStatus(localAlertText),
                                    getCounties(localAlertText),
                                    startAndStopAlertDates.isEmpty() ? DEFAULT_EMPTY_DATE : startAndStopAlertDates.get(0),
                                    startAndStopAlertDates.size() < 2 ? DEFAULT_EMPTY_DATE : startAndStopAlertDates.get(1),
                                    getAlertProbability(localAlertText),
                                    getAlertDescription(localAlertText),
                                    getAlertSms(localAlertText)
                            )
                    );
                });

        return new ImgwMeteoAlert(
                getAlertNumber(headerOfLocalAlerts),
                getVoivodeship(headerOfLocalAlerts),
                getAlertPublishDate(headerOfLocalAlerts),
                mappedImgwLocalMeteoAlerts
        );
    }

    private String getAlertSms(String localAlertText) {
        return getStringBasedOnSpecificPattern(localAlertText, TextPatterns.ALERT_SMS_ANY_TEXT_ENDED_WITH_RSO)
                .orElse(EMPTY_STRING);
    }

    private String getAlertDescription(String localAlertText) {
        return getStringBasedOnSpecificPattern(localAlertText, TextPatterns.ALERT_BODY_PRZEBIEG_ANY_TEXT_ENDED_WITH_SMS)
                .orElse(EMPTY_STRING);
    }

    private String getAlertProbability(String localAlertText) {
        return getStringBasedOnSpecificPattern(localAlertText, TextPatterns.ALERT_PROBABILITY_WORD_WITH_DIGITS_AND_PERCENTAGE)
                .orElse(EMPTY_STRING);
    }

    private AlertStatus getAlertStatus(String localAlertText) {
        String alertStatusText = getStringBasedOnSpecificPattern(localAlertText, TextPatterns.ALERT_STATUS_SLASH_DIGIT_SPACE_CAPITAL_LETTERS)
                .orElse("nowy");
        return AlertStatus.valueOfLabel(alertStatusText.toLowerCase())
                .orElse(AlertStatus.NEW);
    }

    private int getAlertDegree(String localAlertText) {
        return getNumberBasedOdSpecificPattern(localAlertText, TextPatterns.ALERT_DEGREE_SLASH_DIGIT_NEWLINE)
                .orElse(NUMBER_NOT_FOUND);
    }

    private String getAlertType(String localAlertText) {
        return getStringBasedOnSpecificPattern(localAlertText, TextPatterns.ALERT_TYPE_CAPITAL_LETTER_SMALL_LETTERS_SLASH)
                .orElse(UNSPECIFIED);
    }

    private LocalDateTime getAlertPublishDate(String headerOfActualAlerts) {
        return getStringBasedOnSpecificPattern(headerOfActualAlerts, TextPatterns.ALERT_DATE)
                .filter(this::isValidDate)
                .map(textDate -> LocalDateTime.parse(textDate, dateFormatter))
                .orElse(DATETIME_NOW);
    }

    private int getAlertNumber(String headerOfActualAlerts) {
        return getNumberBasedOdSpecificPattern(headerOfActualAlerts, TextPatterns.ALERT_NUMBER)
                .orElse(NUMBER_NOT_FOUND);
    }

    private Voivodeship getVoivodeship(String headerOfActualAlerts) {
        return Voivodeship.isInString(headerOfActualAlerts)
                .orElse(Voivodeship.UNDEFINED);
    }

    private boolean isValidDate(String textDate) {
        try {
            LocalDateTime.parse(textDate, dateFormatter);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    private Optional<String> getStringBasedOnSpecificPattern(String searchingText, String stringPattern) {
        Pattern pattern = Pattern.compile(stringPattern);
        return pattern.matcher(searchingText)
                .results()
                .map(matchResult -> matchResult.group(1))
                .findFirst();
    }

    private OptionalInt getNumberBasedOdSpecificPattern(String headerOfActualAlerts, String stringPattern) {
        Pattern pattern = Pattern.compile(stringPattern);
        return pattern.matcher(headerOfActualAlerts)
                .results()
                .map(matchResult -> matchResult.group(1))
                .mapToInt(Integer::parseInt)
                .findFirst();
    }

    private Map<String, Integer> getCounties(String searchingText) {
        Pattern pattern = Pattern.compile(TextPatterns.ALERT_COUNTIES_ANY_LETTERS_AND_DIGITS_IN_BRACKETS);
        return pattern.matcher(searchingText)
                .results()
                .collect(Collectors.toMap(matchResult -> matchResult.group(1),
                        ImgwAlertMapper::convertToInt));
    }

    private static Integer convertToInt(MatchResult matchResult) {
        try {
            return Integer.parseInt(matchResult.group(2));
        } catch (NumberFormatException e) {
            return NUMBER_NOT_FOUND;
        }
    }

    private List<LocalDateTime> getAlertStartAndStopDate(String searchingText) {
        Pattern pattern = Pattern.compile(TextPatterns.ALERT_DATE);
        return pattern.matcher(searchingText)
                .results()
                .map(matchResult -> matchResult.group(1))
                .filter(this::isValidDate)
                .map(textDate -> LocalDateTime.parse(textDate, dateFormatter))
                .toList();
    }
}
