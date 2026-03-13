package phonxcore

// CoreCallbackHandler is implemented on the Java side to receive Xray lifecycle events.
// gomobile maps Go int → Java long.
type CoreCallbackHandler interface {
	Startup() int
	Shutdown() int
	OnEmitStatus(code int, msg string) int
}

// PsiphonCallbackHandler is implemented on the Java side to receive Psiphon tunnel events.
type PsiphonCallbackHandler interface {
	OnConnecting()
	OnConnected(socksPort int)
	OnHomepage(url string)
	OnDisconnected()
	OnError(message string)
	// BindToDevice protects a socket fd from the TUN routing loop.
	// Java implementation should call VpnService.protect(fileDescriptor).
	BindToDevice(fileDescriptor int) error
}
