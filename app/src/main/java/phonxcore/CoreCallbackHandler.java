package phonxcore;

/**
 * STUB — will be replaced by phonxcore.aar (gomobile-generated).
 * Delete this file and the entire phonxcore/ source directory once the AAR is built.
 */
public interface CoreCallbackHandler {
    long startup();
    long shutdown();
    long onEmitStatus(long code, String msg);
}
