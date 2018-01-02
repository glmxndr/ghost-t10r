package org.exetasys.libs.ghostt10r.msg;

import org.junit.Test;

import java.util.Locale;

public class SampleMessageTest {

    private Locale fr = Locale.forLanguageTag("fr-FR");
    private Locale en = Locale.forLanguageTag("en-UK");

    @Test
    public void testSingleNumber() {

        System.out.println(SampleMessage.SINGLE_NUMBER.format(12.3456).apply(fr));
    }

}
