package com.shorty.url;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shorty.api.ApiException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DestinationUrlValidatorTest {

    private final DestinationUrlValidator validator = new DestinationUrlValidator(new UrlReputation(List.of("evil.blocked.test")));

    @Test
    void rejectsJavascript() {
        assertInvalid("javascript:alert(1)", "invalid_url");
    }

    @Test
    void rejectsLoopbackLiteral() {
        assertInvalid("http://127.0.0.1/", "invalid_url");
    }

    @Test
    void rejectsLocalhost() {
        assertInvalid("http://localhost/secret", "invalid_url");
    }

    @Test
    void rejectsRfc1918() {
        assertInvalid("http://10.0.0.1/admin", "invalid_url");
        assertInvalid("http://192.168.1.20/", "invalid_url");
        assertInvalid("http://172.16.0.9/", "invalid_url");
    }

    @Test
    void rejectsUniqueLocalIpv6() {
        assertInvalid("http://[fc00::1]/", "invalid_url");
    }

    @Test
    void rejectsDenylistedHostWithoutDns() {
        DestinationUrlValidator v = new DestinationUrlValidator(
                new UrlReputation(List.of("evil.blocked.test")), host -> {
                    throw new AssertionError("DNS should not run for denylisted hosts");
                });
        assertThatThrownBy(() -> v.validate("https://evil.blocked.test/phish"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThatCode((ApiException) ex, "blocked_url"));
        assertThatThrownBy(() -> v.validate("https://sub.evil.blocked.test/x"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThatCode((ApiException) ex, "blocked_url"));
    }

    @Test
    void rejectsIpv4MappedPrivate() throws Exception {
        InetAddress mapped = InetAddress.getByName("::ffff:10.0.0.1");
        DestinationUrlValidator v = new DestinationUrlValidator(
                new UrlReputation(List.of()), host -> new InetAddress[] {mapped});
        assertThatThrownBy(() -> v.validate("https://mapped.example/"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThatCode((ApiException) ex, "invalid_url"));
    }

    @Test
    void failClosedOnDnsFailure() {
        DestinationUrlValidator v = new DestinationUrlValidator(
                new UrlReputation(List.of()), host -> {
                    throw new UnknownHostException(host);
                });
        assertThatThrownBy(() -> v.validate("https://no-such-host.invalid/"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThatCode((ApiException) ex, "invalid_url"));
    }

    @Test
    void allowsPublicHost() throws Exception {
        InetAddress pub = InetAddress.getByName("8.8.8.8");
        DestinationUrlValidator v = new DestinationUrlValidator(new UrlReputation(List.of()), h -> new InetAddress[] {pub});
        v.validate("https://example.com/docs");
    }

    private void assertInvalid(String url, String code) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThatCode((ApiException) ex, code));
    }

    private static void assertThatCode(ApiException ex, String code) {
        org.assertj.core.api.Assertions.assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo(code);
    }
}
