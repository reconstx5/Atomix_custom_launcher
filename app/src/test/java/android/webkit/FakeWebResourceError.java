package android.webkit;

/** WebResourceError's constructor is package-private; tests build one from this package. */
public class FakeWebResourceError extends WebResourceError {
    @Override public int getErrorCode() { return WebViewClient.ERROR_CONNECT; }
    @Override public CharSequence getDescription() { return "net::ERR_CONNECTION_REFUSED"; }
}
