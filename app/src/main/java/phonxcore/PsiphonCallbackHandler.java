package phonxcore;

/**
 * STUB — will be replaced by phonxcore.aar (gomobile-generated).
 * Delete this file and the entire phonxcore/ source directory once the AAR is built.
 */
public interface PsiphonCallbackHandler {
    void onConnecting();
    void onConnected(long socksPort);
    void onHomepage(String url);
    void onDisconnected();
    void onError(String message);
    void bindToDevice(long fileDescriptor) throws Exception;
}
