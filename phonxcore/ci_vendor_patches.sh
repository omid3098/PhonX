#!/bin/bash
# Patches applied after `go mod vendor` to fix Go 1.25 compat issues
# in vendored dependencies. These match the local vendor/ patches.
set -euo pipefail
cd "$(dirname "$0")"

echo "Patching vendored dependencies..."

# ── tailscale/netlink: add explicit type casts (Go 1.25 rejects implicit int conversions) ──
QDISC="vendor/github.com/tailscale/netlink/qdisc_linux.go"
sed -i 's/opt\.TcSfqQopt\.Quantum = qdisc\.Quantum/opt.TcSfqQopt.Quantum = uint32(qdisc.Quantum)/' "$QDISC"
sed -i 's/opt\.TcSfqQopt\.Divisor = qdisc\.Divisor/opt.TcSfqQopt.Divisor = uint32(qdisc.Divisor)/' "$QDISC"
sed -i 's/sfq\.Quantum = opt\.TcSfqQopt\.Quantum/sfq.Quantum = uint8(opt.TcSfqQopt.Quantum)/' "$QDISC"
sed -i 's/sfq\.Divisor = opt\.TcSfqQopt\.Divisor/sfq.Divisor = uint8(opt.TcSfqQopt.Divisor)/' "$QDISC"

# ── tailscale/netlink: ToIPNet signature gained a second arg (Family) ──
XFRM="vendor/github.com/tailscale/netlink/xfrm_policy_linux.go"
sed -i 's/\.ToIPNet(msg\.Sel\.PrefixlenD)/\.ToIPNet(msg.Sel.PrefixlenD, msg.Sel.Family)/g' "$XFRM"
sed -i 's/\.ToIPNet(msg\.Sel\.PrefixlenS)/\.ToIPNet(msg.Sel.PrefixlenS, msg.Sel.Family)/g' "$XFRM"

# ── quic-go/qpack: restore backward-compat API (NewDecoder variadic + DecodeFull shim) ──
QPACK="vendor/github.com/quic-go/qpack/decoder.go"
# Fix NewDecoder signature to accept optional callback
sed -i 's/^func NewDecoder() \*Decoder {$/func NewDecoder(opts ...func(HeaderField)) *Decoder {/' "$QPACK"
# Insert DecodeFull method after NewDecoder closing brace (first })
# We use awk since sed multi-line insert varies across platforms
awk '
/^func NewDecoder/,/^}$/ {
  print
  if (/^}$/) {
    print ""
    print "// DecodeFull decodes all header fields from the given header block at once."
    print "// Backward-compatibility shim for qpack v0.4.0 API."
    print "func (d *Decoder) DecodeFull(p []byte) ([]HeaderField, error) {"
    print "\tdecode := d.Decode(p)"
    print "\tvar fields []HeaderField"
    print "\tfor {"
    print "\t\thf, err := decode()"
    print "\t\tif err == io.EOF {"
    print "\t\t\tbreak"
    print "\t\t}"
    print "\t\tif err != nil {"
    print "\t\t\treturn nil, err"
    print "\t\t}"
    print "\t\tfields = append(fields, hf)"
    print "\t}"
    print "\treturn fields, nil"
    print "}"
    inserted = 1
  }
  next
}
{ print }
' "$QPACK" > "${QPACK}.tmp" && mv "${QPACK}.tmp" "$QPACK"

# ── psiphon-tls: add CurveID field to ConnectionState (Go 1.25 added it to crypto/tls) ──
# Go 1.25 added CurveID to tls.ConnectionState (17 fields vs 16).
# psiphon-tls uses unsafe.Pointer casts and panics if field count mismatches.
PSIPHON_TLS_COMMON="vendor/github.com/Psiphon-Labs/psiphon-tls/common.go"
awk '
/CipherSuite uint16$/ {
  print
  print ""
  print "\t// CurveID is the key exchange mechanism used for the connection."
  print "\t// Added in Go 1.25."
  print "\tCurveID CurveID"
  next
}
{ print }
' "$PSIPHON_TLS_COMMON" > "${PSIPHON_TLS_COMMON}.tmp" && mv "${PSIPHON_TLS_COMMON}.tmp" "$PSIPHON_TLS_COMMON"

echo "Vendor patches applied."
