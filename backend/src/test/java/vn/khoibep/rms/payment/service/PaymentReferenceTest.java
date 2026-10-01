package vn.khoibep.rms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** BR-14, BR-15. */
class PaymentReferenceTest {

    @RepeatedTest(20)
    void generatedCodeHasPrefixAndNoLookAlikeCharacters() {
        assertThat(PaymentReference.generate()).matches("KB[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    void findsCodeDespiteSpacesLowerCaseAndExtraText() {
        assertThat(PaymentReference.candidates("NGUYEN VAN A chuyen tien kb a7k3 qx9m cam on"))
                .containsExactly("KBA7K3QX9M");
    }

    @Test
    void looksAtSepayCodeFieldThenContent() {
        assertThat(PaymentReference.candidates("KBAAAAAAAA", "noi dung KBBBBBBBBB"))
                .containsExactly("KBAAAAAAAA", "KBBBBBBBBB");
    }

    @Test
    void keepsOverlappingCandidates() {
        assertThat(PaymentReference.candidates("KBKBA7K3QX9M")).contains("KBA7K3QX9M");
    }

    @Test
    void returnsNothingWhenThereIsNoCode() {
        assertThat(PaymentReference.candidates("chuyen tien an trua", null)).isEmpty();
    }
}
