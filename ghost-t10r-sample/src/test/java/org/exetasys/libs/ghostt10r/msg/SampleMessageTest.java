package org.exetasys.libs.ghostt10r.msg;

import org.junit.Test;

import java.util.Locale;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class SampleMessageTest {

    private Locale fr = Locale.forLanguageTag("fr-FR");
    private Locale en = Locale.forLanguageTag("en-UK");

    @Test
    public void testSingleNumber() {
        String msgFr = SampleMessage.SINGLE_NUMBER.format(12.3456).apply(fr);
        assertThat(msgFr).contains("12.346");
        String msgEn = SampleMessage.SINGLE_NUMBER.format(12.3456).apply(en);
        assertThat(msgEn).contains("12.35");
    }

    @Test
    public void testStringAndInteger() {
        String msgFr = SampleMessage.STRING_AND_INTEGER.format(12, "okFr").apply(fr);
        assertThat(msgFr).contains("12").contains("'okFr'");

        String msgEn = SampleMessage.STRING_AND_INTEGER.format(13, "okEn").apply(en);
        assertThat(msgEn).contains("13").contains("'okEn'");
    }
}
