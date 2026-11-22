# Run your own bore relay (e.g. in Mumbai)

A relay near your players makes joining faster than a far-away public relay.

1. Get a small Linux server (VPS) in a Mumbai / India region from any cloud provider. Check the
   provider's current free tier and pricing yourself.
2. Install bore on it (needs Rust, or download a release binary from https://github.com/ekzhang/bore/releases):
   `cargo install bore-cli`
3. Run the relay with a secret and a port range (so strangers cannot use it):
   `bore server --secret YOUR_SECRET --min-port 25000 --max-port 25100`
4. Open these ports in the provider's firewall AND the OS firewall:
   TCP 7835 (control) and TCP 25000-25100 (public server ports).
5. In Blanc Launcher: Host -> your server -> Settings -> Public tunnel
   - Tunnel relay: the VPS IP address or domain
   - Relay secret: YOUR_SECRET
6. Save, then Start. The Join address shows `<vps>:<port>`.

Keep it running after you log out, e.g. with systemd or `tmux`.
