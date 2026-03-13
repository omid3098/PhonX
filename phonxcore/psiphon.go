package phonxcore

import (
	"encoding/json"
	"os"
	"strings"
	"sync"

	"github.com/Psiphon-Labs/psiphon-tunnel-core/MobileLibrary/psi"
)

// PsiphonController manages the Psiphon tunnel lifecycle.
type PsiphonController struct {
	handler   PsiphonCallbackHandler
	running   bool
	socksPort int
	mu        sync.Mutex
}

// NewPsiphonController creates a new Psiphon controller.
func NewPsiphonController(handler PsiphonCallbackHandler) *PsiphonController {
	return &PsiphonController{handler: handler}
}

// Start begins the Psiphon tunnel.
// configJson is the Psiphon configuration JSON.
// dataDir is used for Psiphon's persistent data store.
// The handler's OnConnected callback fires with the SOCKS port when the tunnel is ready.
func (p *PsiphonController) Start(configJson string, dataDir string) error {
	p.mu.Lock()
	if p.running {
		p.mu.Unlock()
		return nil
	}
	p.mu.Unlock()

	if p.handler != nil {
		p.handler.OnConnecting()
	}

	// Ensure data directory exists
	if err := os.MkdirAll(dataDir, 0700); err != nil {
		p.notifyError("Data dir error: " + err.Error())
		return err
	}

	// Inject DataRootDirectory into the config JSON so Psiphon writes
	// its datastore to the app's writable storage.
	var cfg map[string]interface{}
	if err := json.Unmarshal([]byte(configJson), &cfg); err != nil {
		return err
	}
	cfg["DataRootDirectory"] = dataDir
	updatedConfig, err := json.Marshal(cfg)
	if err != nil {
		return err
	}
	configJson = string(updatedConfig)

	provider := &psiProvider{handler: p.handler, controller: p}

	p.mu.Lock()
	p.running = true
	p.mu.Unlock()

	// psi.Start() launches the tunnel in a goroutine and returns immediately.
	err = psi.Start(
		configJson,
		"",       // embeddedServerEntryList (empty — use config's server list)
		"",       // embeddedServerEntryListFilename
		provider, // implements psi.PsiphonProvider
		true,     // useDeviceBinder — calls provider.BindToDevice for VPN protect()
		false,    // useIPv6Synthesizer
		false,    // useHasIPv6RouteGetter
	)
	if err != nil {
		p.mu.Lock()
		p.running = false
		p.mu.Unlock()
		p.notifyError("Start error: " + err.Error())
		return err
	}

	return nil
}

// Stop shuts down the Psiphon tunnel.
func (p *PsiphonController) Stop() {
	psi.Stop()

	p.mu.Lock()
	p.running = false
	p.socksPort = 0
	p.mu.Unlock()
}

// GetIsRunning returns whether the Psiphon tunnel is active.
func (p *PsiphonController) GetIsRunning() bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.running
}

// GetSOCKSPort returns the local SOCKS proxy port, or 0 if not running.
func (p *PsiphonController) GetSOCKSPort() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.socksPort
}

// notifyError calls the handler's OnError if set.
func (p *PsiphonController) notifyError(msg string) {
	if p.handler != nil {
		p.handler.OnError(msg)
	}
}

// ── psi.PsiphonProvider implementation ─────────────────────────────────────

// psiProvider implements psi.PsiphonProvider and bridges to our
// PsiphonCallbackHandler interface.
type psiProvider struct {
	handler    PsiphonCallbackHandler
	controller *PsiphonController
}

// Notice receives JSON-formatted Psiphon notices and dispatches callbacks.
func (pp *psiProvider) Notice(noticeJSON string) {
	var notice struct {
		NoticeType string          `json:"noticeType"`
		Data       json.RawMessage `json:"data"`
	}
	if err := json.Unmarshal([]byte(noticeJSON), &notice); err != nil {
		return
	}

	switch notice.NoticeType {
	case "ListeningSocksProxyPort":
		var data struct {
			Port int `json:"port"`
		}
		if json.Unmarshal(notice.Data, &data) == nil && data.Port > 0 {
			pp.controller.mu.Lock()
			pp.controller.socksPort = data.Port
			pp.controller.mu.Unlock()
		}

	case "Tunnels":
		var data struct {
			Count int `json:"count"`
		}
		if json.Unmarshal(notice.Data, &data) == nil {
			if data.Count > 0 {
				pp.controller.mu.Lock()
				port := pp.controller.socksPort
				pp.controller.mu.Unlock()
				if pp.handler != nil {
					pp.handler.OnConnected(port)
				}
			} else {
				// Tunnel count dropped to 0 — reconnecting
				if pp.handler != nil {
					pp.handler.OnConnecting()
				}
			}
		}

	case "Homepage":
		var data struct {
			URL string `json:"url"`
		}
		if json.Unmarshal(notice.Data, &data) == nil && pp.handler != nil {
			pp.handler.OnHomepage(data.URL)
		}

	case "Alert":
		// Forward alert messages as errors
		if pp.handler != nil {
			var data struct {
				Message string `json:"message"`
			}
			if json.Unmarshal(notice.Data, &data) == nil &&
				strings.Contains(strings.ToLower(data.Message), "error") {
				pp.handler.OnError(data.Message)
			}
		}
	}
}

func (pp *psiProvider) HasNetworkConnectivity() int { return 1 }
func (pp *psiProvider) GetNetworkID() string        { return "WIFI" }
func (pp *psiProvider) IPv6Synthesize(addr string) string { return addr }
func (pp *psiProvider) HasIPv6Route() int           { return 0 }
func (pp *psiProvider) GetDNSServersAsString() string { return "8.8.8.8,8.8.4.4,1.1.1.1" }

// BindToDevice protects a socket fd from being routed through the TUN interface.
// The Java side should call VpnService.protect(fd) in the implementation.
func (pp *psiProvider) BindToDevice(fileDescriptor int) (string, error) {
	if pp.handler != nil {
		return "", pp.handler.BindToDevice(fileDescriptor)
	}
	return "", nil
}
