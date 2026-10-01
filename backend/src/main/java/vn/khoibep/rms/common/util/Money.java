package vn.khoibep.rms.common.util;

import java.util.Locale;

/** Amounts written the Vietnamese way, as staff read them in messages: "1.230.000 đ". */
public final class Money {

    private Money() {
    }

    public static String vnd(long amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + " đ";
    }
}
