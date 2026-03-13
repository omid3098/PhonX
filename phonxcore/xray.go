package phonxcore

import (
	libv2ray "github.com/2dust/AndroidLibXrayLite"
)

// InitXrayEnv initializes the Xray core environment.
// Must be called once before StartLoop.
func InitXrayEnv(assetsPath string, key string) {
	libv2ray.InitCoreEnv(assetsPath, key)
}

// CheckVersionX returns the Xray core version string.
func CheckVersionX() string {
	return libv2ray.CheckVersionX()
}

// XrayController wraps the running Xray instance.
type XrayController struct {
	inner *libv2ray.CoreController
}

// coreCallbackAdapter converts phonxcore.CoreCallbackHandler
// to libv2ray.CoreCallbackHandler so the Java callback works
// with the underlying library.
type coreCallbackAdapter struct {
	handler CoreCallbackHandler
}

func (a *coreCallbackAdapter) Startup() int            { return a.handler.Startup() }
func (a *coreCallbackAdapter) Shutdown() int            { return a.handler.Shutdown() }
func (a *coreCallbackAdapter) OnEmitStatus(c int, m string) int { return a.handler.OnEmitStatus(c, m) }

// NewXrayController creates a new controller.
func NewXrayController(handler CoreCallbackHandler) *XrayController {
	adapter := &coreCallbackAdapter{handler: handler}
	inner := libv2ray.NewCoreController(adapter)
	return &XrayController{inner: inner}
}

// StartLoop starts Xray with the given JSON config and TUN fd.
func (c *XrayController) StartLoop(configJson string, tunFd int32) error {
	return c.inner.StartLoop(configJson, tunFd)
}

// StopLoop stops the Xray instance.
func (c *XrayController) StopLoop() error {
	return c.inner.StopLoop()
}

// GetIsRunning returns whether Xray is currently running.
func (c *XrayController) GetIsRunning() bool {
	return c.inner.IsRunning
}
